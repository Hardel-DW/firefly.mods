package fr.hardel.firefly.server;

import fr.hardel.firefly.engine.ChunkWork;
import fr.hardel.firefly.engine.LevelLight;
import fr.hardel.firefly.engine.LightEmission;
import fr.hardel.firefly.storage.ChunkLight;
import fr.hardel.firefly.storage.LightColumn;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskDispatcher;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import org.jspecify.annotations.Nullable;

public final class FireflyLightEngine extends ThreadedLevelLightEngine {
    private final LightChunkGetter source;
    private final LevelLight light;
    private final int minSection;
    private final LightReader block;
    private final @Nullable LightReader sky;
    private final Long2ObjectLinkedOpenHashMap<ChunkWork> pending = new Long2ObjectLinkedOpenHashMap<>();

    public FireflyLightEngine(LightChunkGetter source, ChunkMap chunkMap, boolean hasSkyLight, ConsecutiveExecutor executor, ChunkTaskDispatcher dispatcher,
        LightEmission emission) {
        super(source, chunkMap, hasSkyLight, executor, dispatcher);
        this.source = source;
        this.minSection = getMinLightSection();
        this.light = new LevelLight(source, emission, hasSkyLight, this.minSection, getLightSectionCount());
        this.block = new LightReader(this, source, LightLayer.BLOCK, this.minSection);
        this.sky = hasSkyLight ? new LightReader(this, source, LightLayer.SKY, this.minSection) : null;
    }

    @Override
    public void checkBlock(BlockPos pos) {
        work(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())).check(pos);
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean sectionEmpty) {
        work(pos.x(), pos.z()).sectionStatus(pos.y() - this.minSection, sectionEmpty);
    }

    @Override
    public void setLightEnabled(ChunkPos pos, boolean enable) {
        work(pos.x(), pos.z()).lightEnabled(enable);
    }

    @Override
    public void propagateLightSources(ChunkPos pos) {
        work(pos.x(), pos.z()).propagateLightSources();
    }

    @Override
    public int runLightUpdates() {
        int chunks = this.pending.size();
        while (!this.pending.isEmpty()) {
            long chunk = this.pending.firstLongKey();
            this.light.run(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), this.pending.removeFirst());
        }

        return chunks;
    }

    @Override
    public boolean hasLightWork() {
        return !this.pending.isEmpty();
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
        LightColumn column = chunk == null ? null : ((ChunkLight) chunk).firefly$lightIfPresent();
        return column != null && column.sections(LightLayer.BLOCK).enabled() && (this.sky == null || column.sections(LightLayer.SKY).enabled());
    }

    private ChunkWork work(int chunkX, int chunkZ) {
        return this.pending.computeIfAbsent(ChunkPos.pack(chunkX, chunkZ), _ -> new ChunkWork());
    }
}
