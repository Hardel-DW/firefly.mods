package fr.hardel.firefly.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;

final class LightOracle {
    static final Engine VANILLA = (world, sky) -> new LevelLightEngine(world, true, sky);
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
    interface Engine {
        LevelLightEngine create(LightChunkGetter world, boolean sky);
    }

    static List<String> differences(Engine tested, boolean missing) {
        List<String> differences = new ArrayList<>();
        for (long seed = FIRST_SEED; seed < FIRST_SEED + WORLDS; seed++) {
            for (int[] height : HEIGHTS) {
                differences.addAll(compare(tested, seed, height[0], height[1], true, missing));
                differences.addAll(compare(tested, seed, height[0], height[1], false, missing));
            }
        }

        return differences;
    }

    private static List<String> compare(Engine tested, long seed, int minY, int height, boolean sky, boolean missing) {
        String world = "seed %d, height %d to %d, sky %s".formatted(seed, minY, minY + height, sky);
        TestWorld reference = new TestWorld(seed, minY, height);
        TestWorld candidate = new TestWorld(seed, minY, height);
        LevelLightEngine referenceEngine = VANILLA.create(reference, sky);
        LevelLightEngine candidateEngine = tested.create(candidate, sky);
        reference.light(referenceEngine);
        candidate.light(candidateEngine);
        List<String> differences = new ArrayList<>(read("%s, generated".formatted(world), referenceEngine, candidateEngine));

        RandomSource edits = RandomSource.create(~seed);
        for (int edit = 0; edit < EDITS; edit++) {
            BlockPos pos = reference.randomPos(edits);
            BlockState state = TestWorld.PALETTE.get(edits.nextInt(TestWorld.PALETTE.size()));
            BiConsumer<BlockPos, BlockState> write = missing && edit % MISSED_EVERY == 0 ? candidate::setUnseen : candidate::set;
            reference.set(pos, state);
            write.accept(pos, state);
            if (edit % EDITS_PER_BATCH == 0) {
                referenceEngine.runLightUpdates();
                candidateEngine.runLightUpdates();
            }
        }

        referenceEngine.runLightUpdates();
        candidateEngine.runLightUpdates();
        differences.addAll(read("%s, edited".formatted(world), referenceEngine, candidateEngine));
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
}
