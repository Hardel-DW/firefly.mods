package fr.hardel.firefly.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;

record AxiomBuffer(TestWorld world, LevelLightEngine engine, boolean updatesSources) {
    Map<SectionPos, List<BlockPos>> write(List<TestWorld.Edit> edits) {
        Map<SectionPos, List<TestWorld.Edit>> sections = edits.stream().collect(Collectors.groupingBy(edit -> SectionPos.of(edit.pos()), LinkedHashMap::new, Collectors.toList()));
        Map<SectionPos, List<BlockPos>> changed = new LinkedHashMap<>();
        sections.forEach((pos, written) -> {
            List<BlockPos> lightChanged = writeSection(pos, written);
            if (!lightChanged.isEmpty()) {
                changed.put(pos, lightChanged);
            }
        });
        return changed;
    }

    private List<BlockPos> writeSection(SectionPos pos, List<TestWorld.Edit> edits) {
        ProtoChunk chunk = this.world.chunk(pos.origin());
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(pos.y()));
        boolean wasEmpty = section.hasOnlyAir();
        List<BlockPos> lightChanged = edits.stream().filter(edit -> write(chunk, section, edit)).map(TestWorld.Edit::pos).toList();
        if (wasEmpty != section.hasOnlyAir()) {
            this.engine.updateSectionStatus(pos, section.hasOnlyAir());
        }

        return lightChanged;
    }

    private boolean write(ProtoChunk chunk, LevelChunkSection section, TestWorld.Edit edit) {
        BlockPos pos = edit.pos();
        BlockState old = section.setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, edit.state());
        boolean lightChanged = LightEngine.hasDifferentLightProperties(old, edit.state());
        if (lightChanged && this.updatesSources) {
            chunk.getSkyLightSources().update(chunk, pos.getX() & 15, pos.getY(), pos.getZ() & 15);
        }

        return lightChanged;
    }
}
