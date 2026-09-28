package fr.hardel.firefly.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import fr.hardel.firefly.MinecraftBootstrap;
import fr.hardel.firefly.server.FireflyLightEngine;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("mod")
@ExtendWith(MinecraftBootstrap.class)
class QueuedLightTest {
    private static final int CENTER = TestWorld.SIZE / 2;
    private static final int SAVED_BLOCK = 3;
    private static final int SAVED_SKY = 7;

    @Test
    void aLoadedChunkShowsItsSavedSkyWhileItsBlockLightIsPublished() {
        TestWorld world = new TestWorld(0, -16, 64, TestWorld.PALETTE);
        ProtoChunk chunk = world.chunk(new BlockPos(CENTER * 16, world.minBlockY(), CENTER * 16));
        SectionPos section = SectionPos.of(chunk.getPos(), chunk.getSectionYFromSectionIndex(0));
        SkyWatcher watcher = new SkyWatcher(world, section);
        FireflyLightEngine engine = new FireflyLightEngine(watcher, null, true, null, null, LightEmission.VANILLA, Parallel.EXECUTOR);
        watcher.watch(engine.getLayerListener(LightLayer.SKY));

        engine.retainData(chunk.getPos(), true);
        engine.queueSectionData(LightLayer.BLOCK, section, new DataLayer(SAVED_BLOCK));
        engine.queueSectionData(LightLayer.SKY, section, new DataLayer(SAVED_SKY));
        engine.initializeLight(chunk, true).join();
        engine.lightChunk(chunk, true).join();

        assertNotEquals(0, watcher.blockUpdates);
        assertEquals(List.of(), watcher.holes);
        assertEquals(SAVED_SKY, engine.getLayerListener(LightLayer.SKY).getDataLayerData(section).get(0, 0, 0));
    }

    private static final class SkyWatcher implements LightChunkGetter {
        private final LightChunkGetter world;
        private final SectionPos section;
        private final List<String> holes = new ArrayList<>();
        private @Nullable LayerLightEventListener sky;
        private int blockUpdates;

        private SkyWatcher(LightChunkGetter world, SectionPos section) {
            this.world = world;
            this.section = section;
        }

        private void watch(LayerLightEventListener sky) {
            this.sky = sky;
        }

        @Override
        public @Nullable LightChunk getChunkForLighting(int x, int z) {
            return this.world.getChunkForLighting(x, z);
        }

        @Override
        public BlockGetter getLevel() {
            return this.world.getLevel();
        }

        @Override
        public void onLightUpdate(LightLayer layer, SectionPos pos) {
            if (layer != LightLayer.BLOCK || !pos.equals(this.section) || this.sky == null)
                return;

            this.blockUpdates++;
            DataLayer data = this.sky.getDataLayerData(pos);
            if (data == null || data.get(0, 0, 0) != SAVED_SKY) {
                this.holes.add("sky %s during the block update of %s".formatted(data == null ? "null" : "level %d".formatted(data.get(0, 0, 0)), pos));
            }
        }
    }
}
