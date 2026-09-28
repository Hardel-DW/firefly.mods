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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;

final class LightOracle {
    static final Engine<LevelLightEngine> VANILLA = (world, sky) -> new LevelLightEngine(world, true, sky);
    static final Worlds WORLDS = new Worlds(Long.getLong("firefly.oracle.firstSeed", 0), Integer.getInteger("firefly.oracle.worlds", 4),
        List.of(new Height(-16, 64), new Height(0, 48), new Height(-64, 128)), TestWorld.PALETTE);
    private static final int EDITS = 600;
    private static final int EDITS_PER_BATCH = 20;
    private static final int MISSED_EVERY = 50;
    private static final int SHOWN = 5;

    private LightOracle() {
    }

    @FunctionalInterface
    interface Engine<E extends LevelLightEngine> {
        E create(LightChunkGetter world, boolean sky);

        default void light(TestWorld world, E engine) {
            world.light(engine, this);
        }

        default void settle(E engine) {
            engine.runLightUpdates();
        }
    }

    static <E extends LevelLightEngine> List<String> differences(Engine<E> tested, boolean missing) {
        return differences(tested, WORLDS, missing);
    }

    static <E extends LevelLightEngine> List<String> differences(Engine<E> tested, Worlds worlds) {
        return differences(tested, worlds, false);
    }

    static List<String> referenceDifferences() {
        return WORLDS.cases().flatMap(world -> {
            List<TestWorld.Edit> edits = world.edits();
            return read("%s, light from zero twice".formatted(world), world.lightFromZero(edits), world.lightFromZero(edits)).stream();
        }).toList();
    }

    private static <E extends LevelLightEngine> List<String> differences(Engine<E> tested, Worlds worlds, boolean missing) {
        return worlds.cases().flatMap(world -> compare(tested, world, missing).stream()).toList();
    }

    private static <E extends LevelLightEngine> List<String> compare(Engine<E> tested, Case world, boolean missing) {
        TestWorld candidate = world.create();
        E engine = tested.create(candidate, world.sky);
        tested.light(candidate, engine);
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

    static List<String> read(String world, LevelLightEngine reference, LevelLightEngine candidate) {
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

    record Worlds(long firstSeed, int count, List<Height> heights, List<BlockState> palette) {
        private Stream<Case> cases() {
            return LongStream.range(this.firstSeed, this.firstSeed + this.count).boxed().flatMap(seed -> this.heights.stream()
                .flatMap(height -> Stream.of(true, false).map(sky -> new Case(seed, height.minY, height.height, sky, this.palette))));
        }
    }

    record Height(int minY, int height) {
    }

    private record Case(long seed, int minY, int height, boolean sky, List<BlockState> palette) {
        private TestWorld create() {
            return new TestWorld(this.seed, this.minY, this.height, this.palette);
        }

        private List<TestWorld.Edit> edits() {
            LevelHeightAccessor height = LevelHeightAccessor.create(this.minY, this.height);
            RandomSource random = RandomSource.create(~this.seed);
            return Stream.generate(() -> TestWorld.randomEdit(random, height, this.palette)).limit(EDITS).toList();
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
