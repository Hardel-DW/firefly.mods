package fr.hardel.firefly.server;

import fr.hardel.firefly.storage.ChunkLight;
import fr.hardel.firefly.storage.LightColumn;
import fr.hardel.firefly.storage.LightSections;
import fr.hardel.firefly.storage.Nibbles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import org.jspecify.annotations.Nullable;

final class LightReader implements LayerLightEventListener {
    private static final int SKY = 15;

    private final FireflyLightEngine engine;
    private final LightChunkGetter source;
    private final LightLayer layer;
    private final int minSection;

    LightReader(FireflyLightEngine engine, LightChunkGetter source, LightLayer layer, int minSection) {
        this.engine = engine;
        this.source = source;
        this.layer = layer;
        this.minSection = minSection;
    }

    @Override
    public @Nullable DataLayer getDataLayerData(SectionPos pos) {
        LightSections sections = sections(pos.x(), pos.z());
        int index = pos.y() - this.minSection;
        if (sections == null || index < 0 || index >= sections.count())
            return null;

        byte[] data = sections.get(index);
        if (data == null)
            return null;

        if (data == Nibbles.ZERO)
            return new DataLayer();

        return data == Nibbles.FULL ? new DataLayer(SKY) : new DataLayer(data);
    }

    @Override
    public int getLightValue(BlockPos pos) {
        LightSections sections = sections(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        int index = SectionPos.blockToSectionCoord(pos.getY()) - this.minSection;
        if (this.layer == LightLayer.BLOCK)
            return blockLight(sections, index, pos);

        return skyLight(sections, index, pos);
    }

    @Override
    public void checkBlock(BlockPos pos) {
        this.engine.checkBlock(pos);
    }

    @Override
    public boolean hasLightWork() {
        return this.engine.hasLightWork();
    }

    @Override
    public int runLightUpdates() {
        return this.engine.runLightUpdates();
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean sectionEmpty) {
        this.engine.updateSectionStatus(pos, sectionEmpty);
    }

    @Override
    public void setLightEnabled(ChunkPos pos, boolean enable) {
        this.engine.setLightEnabled(pos, enable);
    }

    @Override
    public void propagateLightSources(ChunkPos pos) {
        this.engine.propagateLightSources(pos);
    }

    private static int blockLight(@Nullable LightSections sections, int index, BlockPos pos) {
        if (sections == null || index < 0 || index >= sections.count())
            return 0;

        byte[] data = sections.get(index);
        return data == null ? 0 : Nibbles.get(data, Nibbles.index(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15));
    }

    private static int skyLight(@Nullable LightSections sections, int index, BlockPos pos) {
        if (sections == null)
            return SKY;

        for (int section = Math.max(index, 0); section < sections.count(); section++) {
            byte[] data = sections.get(section);
            if (data != null) {
                return Nibbles.get(data, Nibbles.index(pos.getX() & 15, section == index ? pos.getY() & 15 : 0, pos.getZ() & 15));
            }
        }

        return SKY;
    }

    private @Nullable LightSections sections(int chunkX, int chunkZ) {
        LightChunk chunk = this.source.getChunkForLighting(chunkX, chunkZ);
        LightColumn column = chunk == null ? null : ((ChunkLight) chunk).firefly$lightIfPresent();
        return column == null ? null : column.sections(this.layer);
    }
}
