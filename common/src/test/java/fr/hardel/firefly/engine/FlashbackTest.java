package fr.hardel.firefly.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fr.hardel.firefly.MinecraftBootstrap;
import fr.hardel.firefly.server.FireflyLightEngine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MinecraftBootstrap.class)
class FlashbackTest {
    private static final long SEED = 0;
    private static final int MIN_Y = -16;
    private static final int HEIGHT = 64;
    private static final int EDITS = 200;
    private static final int SHOWN = 5;
    private static final byte[] EMPTY = new byte[0];

    @Test
    @Tag("mod")
    void aReplayShowsTheLightVanillaShows() {
        assertEquals(List.of(), Stream.of(true, false).flatMap(sky -> new Replay(sky).differences().stream()).toList());
    }

    private static final class Replay {
        private final Parallel parallel = new Parallel(LightEmission.VANILLA);
        private final boolean sky;
        private final TestWorld recorded = world();
        private final TestWorld vanillaWorld = world();
        private final TestWorld fireflyWorld = world();
        private final LevelLightEngine recorder;
        private final LevelLightEngine vanilla;
        private final FireflyLightEngine firefly;

        private Replay(boolean sky) {
            this.sky = sky;
            this.recorder = LightOracle.VANILLA.create(this.recorded, sky);
            this.vanilla = LightOracle.VANILLA.create(this.vanillaWorld, sky);
            this.firefly = this.parallel.create(this.fireflyWorld, sky);
            this.recorded.light(this.recorder, LightOracle.VANILLA);
            this.vanillaWorld.chunks().forEach(chunk -> receive(chunk, this.vanilla));
            this.fireflyWorld.chunks().forEach(chunk -> receive(chunk, this.firefly));
        }

        private static void receive(ProtoChunk chunk, LevelLightEngine replay) {
            chunk.setPersistedStatus(ChunkStatus.LIGHT);
            chunk.setLightEngine(replay);
        }

        private List<String> differences() {
            RandomSource random = RandomSource.create(~SEED);
            LevelHeightAccessor height = LevelHeightAccessor.create(MIN_Y, HEIGHT);
            List<TestWorld.Edit> edits = Stream.generate(() -> TestWorld.randomEdit(random, height, TestWorld.PALETTE)).limit(EDITS).toList();
            List<TestWorld.Edit> firstTick = edits.subList(0, EDITS / 2);
            List<TestWorld.Edit> secondTick = edits.subList(EDITS / 2, EDITS);
            packets();
            initialize(false);
            List<String> differences = new ArrayList<>(compare("chunks received"));
            record(firstTick);
            edit(firstTick);
            this.vanilla.runLightUpdates();
            this.parallel.settle(this.firefly);
            packets();
            initialize(true);
            differences.addAll(compare("light received a tick after its blocks"));
            record(secondTick);
            packets();
            edit(secondTick);
            initialize(true);
            differences.addAll(compare("light and blocks received in one tick"));
            return differences;
        }

        private void record(List<TestWorld.Edit> edits) {
            edits.forEach(this.recorded::set);
            this.recorder.runLightUpdates();
        }

        private void packets() {
            this.vanillaWorld.chunks().forEach(chunk -> queuePacket(this.vanilla, chunk.getPos()));
            this.fireflyWorld.chunks().forEach(chunk -> queuePacket(this.firefly, chunk.getPos()));
        }

        private void edit(List<TestWorld.Edit> edits) {
            edits.forEach(this.vanillaWorld::set);
            edits.forEach(this.fireflyWorld::set);
        }

        private void initialize(boolean lighted) {
            this.vanilla.runLightUpdates();
            for (ProtoChunk chunk : this.vanillaWorld.chunks()) {
                chunk.initializeLightSources();
                IntStream.range(0, chunk.getSectionsCount())
                    .filter(index -> !chunk.getSection(index).hasOnlyAir())
                    .forEach(index -> this.vanilla.updateSectionStatus(SectionPos.of(chunk.getPos(), chunk.getSectionYFromSectionIndex(index)), false));
                this.vanilla.runLightUpdates();
                this.vanilla.setLightEnabled(chunk.getPos(), lighted);
                this.vanilla.retainData(chunk.getPos(), false);
            }

            for (ProtoChunk chunk : this.fireflyWorld.chunks()) {
                chunk.initializeLightSources();
                this.firefly.initializeLight(chunk, lighted);
            }

            this.parallel.settle(this.firefly);
        }

        private void queuePacket(LevelLightEngine replay, ChunkPos pos) {
            replay.retainData(pos, true);
            for (LightLayer layer : LightLayer.values()) {
                for (int y = this.recorder.getMinLightSection(); y < this.recorder.getMaxLightSection(); y++) {
                    DataLayer data = this.recorder.getLayerListener(layer).getDataLayerData(SectionPos.of(pos, y));
                    if (data != null) {
                        replay.queueSectionData(layer, SectionPos.of(pos, y), data.isEmpty() ? new DataLayer() : new DataLayer(data.getData().clone()));
                    }
                }
            }
        }

        private List<String> compare(String step) {
            String moment = "sky %s, %s".formatted(this.sky, step);
            List<String> sections = this.vanillaWorld.chunks().stream()
                .flatMap(chunk -> Stream.iterate(this.vanilla.getMinLightSection(), y -> y < this.vanilla.getMaxLightSection(), y -> y + 1)
                    .map(y -> SectionPos.of(chunk.getPos(), y)))
                .flatMap(pos -> Arrays.stream(LightLayer.values())
                    .filter(layer -> !Arrays.equals(sent(this.vanilla, layer, pos), sent(this.firefly, layer, pos)))
                    .map(layer -> "%s %s".formatted(layer, pos)))
                .toList();
            List<String> differences = new ArrayList<>(LightOracle.read(moment, this.vanilla, this.firefly));
            if (!sections.isEmpty()) {
                differences.add("%s: %d sections sent differently, first %s".formatted(moment, sections.size(), sections.subList(0, Math.min(SHOWN, sections.size()))));
            }

            return differences;
        }

        private static TestWorld world() {
            return new TestWorld(SEED, MIN_Y, HEIGHT, TestWorld.PALETTE);
        }

        private static byte @Nullable [] sent(LevelLightEngine engine, LightLayer layer, SectionPos pos) {
            DataLayer data = engine.getLayerListener(layer).getDataLayerData(pos);
            if (data == null)
                return null;

            return data.isEmpty() ? EMPTY : data.copy().getData();
        }
    }
}
