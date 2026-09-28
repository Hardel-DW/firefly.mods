package fr.hardel.firefly.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fr.hardel.firefly.MinecraftBootstrap;
import fr.hardel.firefly.schedule.LightExecutor;
import fr.hardel.firefly.server.FireflyLightEngine;
import fr.hardel.firefly.storage.Nibbles;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.dimension.DimensionType;
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

    private static List<Integer> distinctBytes(byte[] data) {
        return IntStream.range(0, data.length).map(index -> data[index]).distinct().boxed().toList();
    }

    private static final class Held implements LightExecutor, LightOracle.Engine<FireflyLightEngine> {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(LightChunkGetter level, int chunkX, int chunkZ, int radius, Runnable task) {
            this.tasks.add(task);
        }

        @Override
        public FireflyLightEngine create(LightChunkGetter world, boolean sky) {
            return new FireflyLightEngine(world, null, sky, null, null, LightEmission.VANILLA, this);
        }

        @Override
        public void settle(FireflyLightEngine engine) {
            engine.tryScheduleUpdate();
            runUntil(this.tasks::isEmpty);
        }

        private void runUntil(BooleanSupplier done) {
            while (!done.getAsBoolean()) {
                this.tasks.remove().run();
            }
        }
    }
}
