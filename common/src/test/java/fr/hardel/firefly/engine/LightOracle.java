package fr.hardel.firefly.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;

final class LightOracle {
    static final Engine<LevelLightEngine> VANILLA = (world, sky) -> new LevelLightEngine(world, true, sky);
    private static final long FIRST_SEED = Long.getLong("firefly.oracle.firstSeed", 0);
    private static final int WORLDS = Integer.getInteger("firefly.oracle.worlds", 4);
    private static final int[][] HEIGHTS = {{-16, 64}, {0, 48}, {-64, 128}};
    private static final int EDITS = 600;
    private static final int EDITS_PER_BATCH = 20;
    private static final int MISSED_EVERY = 50;
    private static final int SHOWN = 5;

    private LightOracle() {
    }

    @FunctionalInterface
    interface Engine<E extends LevelLightEngine> {
        E create(LightChunkGetter world, boolean sky);

        default void settle(E engine) {
            engine.runLightUpdates();
        }
    }

    static <E extends LevelLightEngine> List<String> differences(Engine<E> tested, boolean missing) {
        return cases().flatMap(world -> compare(tested, world, missing).stream()).toList();
    }

    static List<String> referenceDifferences() {
        return cases().flatMap(world -> {
            List<TestWorld.Edit> edits = world.edits();
            return read("%s, light from zero twice".formatted(world), world.lightFromZero(edits), world.lightFromZero(edits)).stream();
        }).toList();
    }

    private static Stream<Case> cases() {
        return LongStream.range(FIRST_SEED, FIRST_SEED + WORLDS).boxed()
            .flatMap(seed -> Arrays.stream(HEIGHTS).flatMap(height -> Stream.of(true, false).map(sky -> new Case(seed, height[0], height[1], sky))));
    }

    private static <E extends LevelLightEngine> List<String> compare(Engine<E> tested, Case world, boolean missing) {
        TestWorld candidate = world.create();
        E engine = tested.create(candidate, world.sky);
        candidate.light(engine, tested);
        List<String> differences = new ArrayList<>(read("%s, generated".formatted(world), world.lightFromZero(List.of()), engine));

        LightLog log = new LightLog(engine, TestWorld.SIZE);
        candidate.watch(log);
        List<TestWorld.Edit> edits = world.edits();
        for (int first = 0; first < edits.size(); first += EDITS_PER_BATCH) {
            for (int edit = first; edit < Math.min(first + EDITS_PER_BATCH, edits.size()); edit++) {
                Consumer<TestWorld.Edit> write = missing && edit % MISSED_EVERY == 0 ? candidate::setUnseen : candidate::set;
                write.accept(edits.get(edit));
            }

            tested.settle(engine);
            differences.addAll(log.settle("%s, edits %d to %d".formatted(world, first, first + EDITS_PER_BATCH - 1)));
        }

        differences.addAll(read("%s, edited".formatted(world), world.lightFromZero(edits), engine));
        return differences;
    }

    private static List<String> read(String world, LevelLightEngine reference, LevelLightEngine candidate) {
        int minY = SectionPos.sectionToBlockCoord(reference.getMinLightSection());
        int maxY = SectionPos.sectionToBlockCoord(reference.getMaxLightSection()) - 1;
        int max = (TestWorld.SIZE - 1) * 16 - 1;
        Supplier<Stream<String>> differences = () -> Arrays.stream(LightLayer.values()).flatMap(layer -> BlockPos.betweenClosedStream(16, minY, 16, max, maxY, max)
            .filter(pos -> reference.getLayerListener(layer).getLightValue(pos) != candidate.getLayerListener(layer).getLightValue(pos))
            .map(pos -> "%s %s: %d instead of %d".formatted(layer, pos.toShortString(), candidate.getLayerListener(layer).getLightValue(pos),
                reference.getLayerListener(layer).getLightValue(pos))));
        long count = differences.get().count();
        return count == 0 ? List.of() : List.of("%s: %d blocks differ, first %s".formatted(world, count, differences.get().limit(SHOWN).toList()));
    }

    private record Case(long seed, int minY, int height, boolean sky) {
        private TestWorld create() {
            return new TestWorld(this.seed, this.minY, this.height);
        }

        private List<TestWorld.Edit> edits() {
            LevelHeightAccessor height = LevelHeightAccessor.create(this.minY, this.height);
            RandomSource random = RandomSource.create(~this.seed);
            return Stream.generate(() -> TestWorld.randomEdit(random, height)).limit(EDITS).toList();
        }

        private LevelLightEngine lightFromZero(List<TestWorld.Edit> edits) {
            TestWorld world = create();
            edits.forEach(world::set);
            LevelLightEngine engine = VANILLA.create(world, this.sky);
            world.light(engine, VANILLA);
            return engine;
        }

        @Override
        public String toString() {
            return "seed %d, height %d to %d, sky %s".formatted(this.seed, this.minY, this.minY + this.height, this.sky);
        }
    }
}
