package fr.hardel.firefly.server;

import fr.hardel.firefly.Firefly;
import fr.hardel.firefly.engine.ChunkWork;
import fr.hardel.firefly.engine.LevelLight;
import fr.hardel.firefly.engine.LightEmission;
import fr.hardel.firefly.schedule.LightExecutor;
import fr.hardel.firefly.storage.ChunkLight;
import fr.hardel.firefly.storage.LightColumn;
import fr.hardel.firefly.storage.Nibbles;
import fr.hardel.firefly.storage.QueuedSections;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
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
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import org.jspecify.annotations.Nullable;

public final class FireflyLightEngine extends ThreadedLevelLightEngine {
    private static final int FULL = 15;

    private final LightChunkGetter source;
    private final LevelLight light;
    private final QueuedSections queued;
    private final int minSection;
    private final LightReader block;
    private final @Nullable LightReader sky;
    private final LightExecutor executor;
    private final Long2ObjectLinkedOpenHashMap<ChunkWork> unsubmitted = new Long2ObjectLinkedOpenHashMap<>();
    private final Object submitting = new Object();
    private final AtomicInteger outstanding = new AtomicInteger();

    public FireflyLightEngine(LightChunkGetter source, ChunkMap chunkMap, boolean hasSkyLight, ConsecutiveExecutor consecutiveExecutor, ChunkTaskDispatcher dispatcher,
        LightEmission emission, LightExecutor executor) {
        super(source, chunkMap, hasSkyLight, consecutiveExecutor, dispatcher);
        this.source = source;
        this.executor = executor;
        this.minSection = getMinLightSection();
        this.queued = new QueuedSections(getLightSectionCount());
        this.light = new LevelLight(source, emission, this.queued, hasSkyLight, this.minSection, getLightSectionCount());
        this.block = new LightReader(this, source, this.queued, LightLayer.BLOCK, this.minSection);
        this.sky = hasSkyLight ? new LightReader(this, source, this.queued, LightLayer.SKY, this.minSection) : null;
        Firefly.LOGGER.info("Firefly lights {} sections with sky {}, emission {} and executor {}", getLightSectionCount(), hasSkyLight,
            emission.getClass().getSimpleName(), executor.getClass().getSimpleName());
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
        CompletableFuture<ChunkAccess> initialized = new CompletableFuture<>();
        initialize(chunk, lighted, work -> work.completes(initialized, chunk));
        return initialized;
    }

    public CompletableFuture<ChunkAccess> initializeLightAhead(ChunkAccess chunk, boolean lighted) {
        initialize(chunk, lighted, _ -> {});
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public CompletableFuture<ChunkAccess> lightChunk(ChunkAccess chunk, boolean lighted) {
        ChunkPos pos = chunk.getPos();
        CompletableFuture<ChunkAccess> lit = new CompletableFuture<>();
        chunk.setLightCorrect(false);
        request(pos.x(), pos.z(), work -> {
            if (!lighted) {
                work.propagateLightSources();
            }

            work.onComplete(() -> chunk.setLightCorrect(true));
            work.completes(lit, chunk);
        });
        return lit;
    }

    @Override
    public CompletableFuture<?> waitForPendingTasks(int chunkX, int chunkZ) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        submitPending();
        request(chunkX, chunkZ, work -> work.completes(done, null));
        return done;
    }

    @Override
    protected void updateChunkStatus(ChunkPos pos) {
        this.queued.clear(pos.x(), pos.z());
        request(pos.x(), pos.z(), ChunkWork::unload);
    }

    @Override
    public void tryScheduleUpdate() {
        submitPending();
        super.tryScheduleUpdate();
    }

    @Override
    public boolean hasLightWork() {
        return this.outstanding.get() > 0;
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

    private void initialize(ChunkAccess chunk, boolean lighted, Consumer<ChunkWork> awaiting) {
        ChunkPos pos = chunk.getPos();
        request(pos.x(), pos.z(), work -> {
            work.initialize(lighted);
            work.onComplete(() -> this.queued.retain(pos.x(), pos.z(), false));
            awaiting.accept(work);
        });
    }

    private void request(int chunkX, int chunkZ, Consumer<ChunkWork> change) {
        update(chunkX, chunkZ, change);
        submitPending();
    }

    private void update(int chunkX, int chunkZ, Consumer<ChunkWork> change) {
        long chunk = ChunkPos.pack(chunkX, chunkZ);
        synchronized (this.unsubmitted) {
            ChunkWork work = this.unsubmitted.get(chunk);
            if (work != null) {
                change.accept(work);
                return;
            }

            ChunkWork created = new ChunkWork(chunkX, chunkZ);
            change.accept(created);
            this.unsubmitted.put(chunk, created);
            this.outstanding.incrementAndGet();
        }
    }

    private void submitPending() {
        synchronized (this.submitting) {
            takeUnsubmitted().forEach(work -> this.executor.execute(this.source, work.chunkX(), work.chunkZ(), work.radius(), () -> run(work)));
        }
    }

    private List<ChunkWork> takeUnsubmitted() {
        synchronized (this.unsubmitted) {
            List<ChunkWork> works = List.copyOf(this.unsubmitted.values());
            this.unsubmitted.clear();
            return works;
        }
    }

    private void run(ChunkWork work) {
        try {
            this.light.run(work);
        } catch (RuntimeException exception) {
            Firefly.LOGGER.error("Firefly failed to light chunk [{}, {}]", work.chunkX(), work.chunkZ(), exception);
            work.fail(exception);
        } finally {
            this.outstanding.decrementAndGet();
        }
    }

    private static byte[] sectionData(DataLayer data) {
        if (data.isEmpty())
            return Nibbles.ZERO;

        return data.isDefinitelyHomogenous() && data.get(0, 0, 0) == FULL ? Nibbles.FULL : data.getData();
    }
}
