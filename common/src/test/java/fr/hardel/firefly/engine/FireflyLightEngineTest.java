package fr.hardel.firefly.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fr.hardel.firefly.MinecraftBootstrap;
import fr.hardel.firefly.server.FireflyLightEngine;
import fr.hardel.firefly.storage.Nibbles;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("mod")
@ExtendWith(MinecraftBootstrap.class)
class FireflyLightEngineTest {
    private static final int CENTER = TestWorld.SIZE / 2;
    private static final IllegalStateException FAILURE = new IllegalStateException("emission failed");
    private static final LightEmission FAILING = (_, _, _) -> {
        throw FAILURE;
    };
    private static final LightEmission ONE_LEVEL_LOW = (state, _, _) -> Math.max(0, state.getLightEmission() - 1);
    private static final LightOracle.Worlds TALLEST = new LightOracle.Worlds(2, 1, List.of(new LightOracle.Height(DimensionType.MIN_Y, DimensionType.Y_SIZE)), TestWorld.PALETTE);

    @Test
    void fireflyLightsLikeLightFromZeroAndKeepsSharedSections() {
        assertEquals(List.of(), LightOracle.differences(new Parallel(LightEmission.VANILLA), false));
        assertEquals(List.of(0), distinctBytes(Nibbles.ZERO));
        assertEquals(List.of(-1), distinctBytes(Nibbles.FULL));
    }

    @Test
    void theLightDoesNotDependOnTheOrderOfTheTasks() {
        LongStream.rangeClosed(1, 3).forEach(order -> assertEquals(List.of(), LightOracle.differences(new Unordered(order), false), "order %d".formatted(order)));
    }

    @Test
    void changesOfOneTickInNeighborChunksNeverShowAHalfLitState() {
        List<String> flickers = LongStream.of(3019, 3078, 3116, 3174)
            .mapToObj(seed -> new LightOracle.Worlds(seed, 1, LightOracle.WORLDS.heights(), TestWorld.PALETTE))
            .flatMap(worlds -> LightOracle.differences(new Parallel(LightEmission.VANILLA), worlds).stream())
            .toList();

        assertEquals(List.of(), flickers);
    }

    @Test
    void fireflyFillsTheSkySourcesVanillaFills() {
        List<String> differences = LightOracle.WORLDS.heights().stream().flatMap(height -> LongStream.range(0, 2).boxed().flatMap(seed -> {
            TestWorld world = new TestWorld(seed, height.minY(), height.height(), TestWorld.PALETTE);
            Parallel parallel = new Parallel(LightEmission.VANILLA);
            parallel.light(world, parallel.create(world, true));
            return world.chunks().stream().flatMap(FireflyLightEngineTest::sourceDifferences);
        })).toList();

        assertEquals(List.of(), differences);
    }

    @Test
    void theTallestWorldADatapackAllowsLightsLikeLightFromZero() {
        assertEquals(List.of(), LightOracle.differences(new Parallel(LightEmission.VANILLA), TALLEST));
    }

    @Test
    void aFireflyOneLevelLowIsCaught() {
        assertNotEquals(List.of(), LightOracle.differences(new Parallel(ONE_LEVEL_LOW), false));
    }

    @Test
    void aChunkThatThrowsFailsItsFuturesInsteadOfHanging() {
        TestWorld world = new TestWorld(0, -16, 64, TestWorld.PALETTE);
        BlockPos glowstone = new BlockPos(CENTER * 16, 0, CENTER * 16);
        world.set(new TestWorld.Edit(glowstone, Blocks.GLOWSTONE.defaultBlockState()));
        FireflyLightEngine engine = new FireflyLightEngine(world, null, true, null, null, FAILING, Parallel.EXECUTOR);

        CompletableFuture<?> lit = engine.lightChunk(world.chunk(glowstone), false);

        ExecutionException failure = assertThrows(ExecutionException.class, () -> lit.get(10, TimeUnit.SECONDS));
        assertSame(FAILURE, failure.getCause());
    }

    @Test
    void waitingForAChunkWaitsForTheLightItsNeighborsSendIntoIt() {
        TestWorld world = new TestWorld(0, -16, 64, TestWorld.PALETTE);
        BlockPos lit = new BlockPos(CENTER * 16, 40, CENTER * 16 + 8);
        BlockPos glowstone = lit.west();
        world.setUnseen(new TestWorld.Edit(lit, Blocks.AIR.defaultBlockState()));
        world.setUnseen(new TestWorld.Edit(glowstone, Blocks.AIR.defaultBlockState()));
        Held held = new Held();
        FireflyLightEngine engine = held.create(world, true);
        world.light(engine, held);

        engine.checkBlock(lit);
        world.set(new TestWorld.Edit(glowstone, Blocks.GLOWSTONE.defaultBlockState()));
        CompletableFuture<?> waited = engine.waitForPendingTasks(CENTER, CENTER);
        held.runUntil(waited::isDone);

        assertEquals(Blocks.GLOWSTONE.defaultBlockState().getLightEmission() - 1, engine.getLayerListener(LightLayer.BLOCK).getLightValue(lit));
    }

    @Test
    void onlyTheSectionsWhoseLightChangesAreNotified() {
        TestWorld world = new TestWorld(0, -16, 64, TestWorld.PALETTE);
        BlockPos glowstone = new BlockPos(CENTER * 16 + 8, 31, CENTER * 16 + 8);
        BlockPos.betweenClosed(CENTER * 16, 16, CENTER * 16, CENTER * 16 + 15, 47, CENTER * 16 + 15).forEach(pos ->
            world.setUnseen(new TestWorld.Edit(pos.immutable(), (pos.getY() < 32 ? Blocks.AIR : Blocks.STONE).defaultBlockState())));
        Held held = new Held();
        FireflyLightEngine engine = held.create(world, false);
        world.light(engine, held);
        LightLog log = new LightLog(engine, TestWorld.SIZE);
        world.watch(log);

        world.set(new TestWorld.Edit(glowstone, Blocks.GLOWSTONE.defaultBlockState()));
        held.settle(engine);

        assertEquals(List.of(), log.notifiedUnchanged());
    }

    private static Stream<String> sourceDifferences(ProtoChunk chunk) {
        ChunkSkyLightSources vanilla = new ChunkSkyLightSources(chunk);
        vanilla.fillFrom(chunk);
        return IntStream.range(0, 256)
            .filter(column -> vanilla.getLowestSourceY(column & 15, column >> 4) != chunk.getSkyLightSources().getLowestSourceY(column & 15, column >> 4))
            .mapToObj(column -> "%s column %d: %d instead of %d".formatted(chunk.getPos(), column, chunk.getSkyLightSources().getLowestSourceY(column & 15, column >> 4),
                vanilla.getLowestSourceY(column & 15, column >> 4)));
    }

    private static List<Integer> distinctBytes(byte[] data) {
        return IntStream.range(0, data.length).map(index -> data[index]).distinct().boxed().toList();
    }
}
