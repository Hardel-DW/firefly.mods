package fr.hardel.firefly.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import fr.hardel.firefly.MinecraftBootstrap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MinecraftBootstrap.class)
class LightOracleTest {
    @Test
    void theLightFromZeroIsTheSameTwice() {
        assertEquals(List.of(), LightOracle.referenceDifferences());
    }

    @Test
    void aMissedUpdateIsCaught() {
        assertNotEquals(List.of(), LightOracle.differences(LightOracle.VANILLA, true));
    }

    @Test
    void aFlickerIsCaught() {
        assertNotEquals(List.of(), LightOracle.differences(Flickering::new, false));
    }

    @Test
    void aMissingNotificationIsCaught() {
        assertNotEquals(List.of(), LightOracle.differences((world, sky) -> new LevelLightEngine(new Silent(world), true, sky), false));
    }

    private static final class Flickering extends LevelLightEngine {
        private final LightChunkGetter world;
        private @Nullable BlockPos checked;
        private boolean dark;

        private Flickering(LightChunkGetter world, boolean sky) {
            super(world, true, sky);
            this.world = world;
        }

        @Override
        public void checkBlock(BlockPos pos) {
            super.checkBlock(pos);
            this.checked = pos.immutable();
        }

        @Override
        public int runLightUpdates() {
            if (this.checked != null) {
                flicker(SectionPos.of(this.checked));
                this.checked = null;
            }

            return super.runLightUpdates();
        }

        @Override
        public LayerLightEventListener getLayerListener(LightLayer layer) {
            return this.dark ? Dark.INSTANCE : super.getLayerListener(layer);
        }

        private void flicker(SectionPos section) {
            this.dark = true;
            this.world.onLightUpdate(LightLayer.BLOCK, section);
            this.world.onLightUpdate(LightLayer.SKY, section);
            this.dark = false;
        }
    }

    private enum Dark implements LayerLightEventListener {
        INSTANCE;

        @Override
        public DataLayer getDataLayerData(SectionPos pos) {
            return new DataLayer();
        }

        @Override
        public int getLightValue(BlockPos pos) {
            return 0;
        }

        @Override
        public void checkBlock(BlockPos pos) {
        }

        @Override
        public boolean hasLightWork() {
            return false;
        }

        @Override
        public int runLightUpdates() {
            return 0;
        }

        @Override
        public void updateSectionStatus(SectionPos pos, boolean sectionEmpty) {
        }

        @Override
        public void setLightEnabled(ChunkPos pos, boolean enable) {
        }

        @Override
        public void propagateLightSources(ChunkPos pos) {
        }
    }

    private record Silent(LightChunkGetter world) implements LightChunkGetter {
        @Override
        public @Nullable LightChunk getChunkForLighting(int x, int z) {
            return this.world.getChunkForLighting(x, z);
        }

        @Override
        public BlockGetter getLevel() {
            return this.world.getLevel();
        }
    }
}
