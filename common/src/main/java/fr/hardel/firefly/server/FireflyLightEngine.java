package fr.hardel.firefly.server;

import fr.hardel.firefly.engine.ChunkWork;
import fr.hardel.firefly.engine.LevelLight;
import fr.hardel.firefly.engine.LightEmission;
import fr.hardel.firefly.storage.ChunkLight;
import fr.hardel.firefly.storage.LightColumn;
import fr.hardel.firefly.storage.Nibbles;
import fr.hardel.firefly.storage.QueuedSections;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskDispatcher;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class FireflyLightEngine extends ThreadedLevelLightEngine {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FULL = 15;

    private final LightChunkGetter source;
    private final LevelLight light;
    private final QueuedSections queued;
    private final int minSection;
    private final LightReader block;
    private final @Nullable LightReader sky;
    private final Executor runner;
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final Long2ObjectLinkedOpenHashMap<ChunkWork> pending = new Long2ObjectLinkedOpenHashMap<>();

    public FireflyLightEngine(LightChunkGetter source, ChunkMap chunkMap, boolean hasSkyLight, ConsecutiveExecutor executor, ChunkTaskDispatcher dispatcher,
        LightEmission emission, Executor runner) {
        super(source, chunkMap, hasSkyLight, executor, dispatcher);
        this.source = source;
        this.runner = runner;
        this.minSection = getMinLightSection();
        this.queued = new QueuedSections(getLightSectionCount());
        this.light = new LevelLight(source, emission, this.queued, hasSkyLight, this.minSection, getLightSectionCount());
        this.block = new LightReader(this, source, this.queued, LightLayer.BLOCK, this.minSection);
        this.sky = hasSkyLight ? new LightReader(this, source, this.queued, LightLayer.SKY, this.minSection) : null;
        LOGGER.info("Firefly lights {} sections with sky {} and emission {}", getLightSectionCount(), hasSkyLight, emission.getClass().getSimpleName());
    }

    @Override
    public void checkBlock(BlockPos pos) {
        BlockPos immutable = pos.immutable();
        update(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), work -> work.check(immutable));
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean sectionEmpty) {
        update(pos.x(), pos.z(), work -> work.sectionStatus(pos.y() - this.minSection, sectionEmpty));
    }

    @Override
    public void setLightEnabled(ChunkPos pos, boolean enable) {
        update(pos.x(), pos.z(), work -> work.lightEnabled(enable));
    }

    @Override
    public void propagateLightSources(ChunkPos pos) {
        update(pos.x(), pos.z(), ChunkWork::propagateLightSources);
    }

    @Override
    public void queueSectionData(LightLayer layer, SectionPos pos, @Nullable DataLayer data) {
        this.queued.queue(layer, pos.x(), pos.z(), pos.y() - this.minSection, data == null ? null : sectionData(data));
    }

    @Override
    public void retainData(ChunkPos pos, boolean retain) {
        this.queued.retain(pos.x(), pos.z(), retain);
    }

    @Override
    public CompletableFuture<ChunkAccess> initializeLight(ChunkAccess chunk, boolean lighted) {
        ChunkPos pos = chunk.getPos();
        CompletableFuture<ChunkAccess> lit = new CompletableFuture<>();
        LevelChunkSection[] sections = chunk.getSections();
        update(pos.x(), pos.z(), work -> {
            for (int index = 0; index < sections.length; index++) {
                if (!sections[index].hasOnlyAir()) {
                    work.sectionStatus(index + 1, false);
                }
            }

            work.lightEnabledAfterUpdate(lighted);
            work.onComplete(() -> {
                this.queued.retain(pos.x(), pos.z(), false);
                lit.complete(chunk);
            });
        });
        return lit;
    }

    @Override
    public CompletableFuture<ChunkAccess> lightChunk(ChunkAccess chunk, boolean lighted) {
        ChunkPos pos = chunk.getPos();
        CompletableFuture<ChunkAccess> lit = new CompletableFuture<>();
        chunk.setLightCorrect(false);
        update(pos.x(), pos.z(), work -> {
            if (!lighted) {
                work.propagateLightSources();
            }

            work.onComplete(() -> {
                chunk.setLightCorrect(true);
                lit.complete(chunk);
            });
        });
        return lit;
    }

    @Override
    public CompletableFuture<?> waitForPendingTasks(int chunkX, int chunkZ) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        update(chunkX, chunkZ, work -> work.onComplete(() -> done.complete(null)));
        return done;
    }

    @Override
    protected void updateChunkStatus(ChunkPos pos) {
        this.queued.clear(pos.x(), pos.z());
        update(pos.x(), pos.z(), ChunkWork::unload);
    }

    @Override
    public void tryScheduleUpdate() {
        schedule();
    }

    @Override
    public int runLightUpdates() {
        int count = 0;
        for (ChunkWork work = take(); work != null; work = take()) {
            this.light.run(work);
            count++;
        }

        return count;
    }

    @Override
    public boolean hasLightWork() {
        synchronized (this.pending) {
            return !this.pending.isEmpty();
        }
    }

    @Override
    public LayerLightEventListener getLayerListener(LightLayer layer) {
        if (layer == LightLayer.BLOCK)
            return this.block;

        return this.sky == null ? LayerLightEventListener.DummyLightLayerEventListener.INSTANCE : this.sky;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int skyDampen) {
        int skyLight = this.sky == null ? 0 : this.sky.getLightValue(pos) - skyDampen;
        return Math.max(this.block.getLightValue(pos), skyLight);
    }

    @Override
    public boolean lightOnInColumn(long sectionZeroNode) {
        LightChunk chunk = this.source.getChunkForLighting(SectionPos.x(sectionZeroNode), SectionPos.z(sectionZeroNode));
        if (chunk == null)
            return false;

        LightColumn column = ((ChunkLight) chunk).firefly$light();
        return column.sections(LightLayer.BLOCK).enabled() && (this.sky == null || column.sections(LightLayer.SKY).enabled());
    }

    private void update(int chunkX, int chunkZ, Consumer<ChunkWork> change) {
        synchronized (this.pending) {
            change.accept(this.pending.computeIfAbsent(ChunkPos.pack(chunkX, chunkZ), _ -> new ChunkWork(chunkX, chunkZ)));
        }

        schedule();
    }

    private void schedule() {
        if (this.scheduled.compareAndSet(false, true)) {
            this.runner.execute(this::drain);
        }
    }

    private void drain() {
        runLightUpdates();
        this.scheduled.set(false);
        if (hasLightWork()) {
            schedule();
        }
    }

    private @Nullable ChunkWork take() {
        synchronized (this.pending) {
            return this.pending.isEmpty() ? null : this.pending.removeFirst();
        }
    }

    private static byte[] sectionData(DataLayer data) {
        if (data.isEmpty())
            return Nibbles.ZERO;

        return data.isDefinitelyHomogenous() && data.get(0, 0, 0) == FULL ? Nibbles.FULL : data.getData();
    }
}
