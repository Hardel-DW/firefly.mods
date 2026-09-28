package fr.hardel.firefly.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fr.hardel.firefly.MinecraftBootstrap;
import fr.hardel.firefly.schedule.ReservingExecutor;
import fr.hardel.firefly.server.FireflyLightEngine;
import fr.hardel.firefly.storage.Nibbles;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("mod")
@ExtendWith(MinecraftBootstrap.class)
class FireflyLightEngineTest {
    private static final ReservingExecutor EXECUTOR = new ReservingExecutor(4);
    private static final int CENTER = TestWorld.SIZE / 2;
    private static final IllegalStateException FAILURE = new IllegalStateException("emission failed");
    private static final LightEmission FAILING = (_, _, _) -> {
        throw FAILURE;
    };
    private static final LightEmission ONE_LEVEL_LOW = (state, _, _) -> Math.max(0, state.getLightEmission() - 1);

    @Test
    void fireflyLightsLikeLightFromZeroAndKeepsSharedSections() {
        assertEquals(List.of(), LightOracle.differences(new Parallel(LightEmission.VANILLA), false));
        assertEquals(List.of(0), distinctBytes(Nibbles.ZERO));
        assertEquals(List.of(-1), distinctBytes(Nibbles.FULL));
    }

    @Test
    void aFireflyOneLevelLowIsCaught() {
        assertNotEquals(List.of(), LightOracle.differences(new Parallel(ONE_LEVEL_LOW), false));
    }

    @Test
    void aChunkThatThrowsFailsItsFuturesInsteadOfHanging() {
        TestWorld world = new TestWorld(0, -16, 64);
        BlockPos glowstone = new BlockPos(CENTER * 16, 0, CENTER * 16);
        world.set(new TestWorld.Edit(glowstone, Blocks.GLOWSTONE.defaultBlockState()));
        FireflyLightEngine engine = new FireflyLightEngine(world, null, true, null, null, FAILING, EXECUTOR);

        CompletableFuture<?> lit = engine.lightChunk(world.chunk(glowstone), false);

        ExecutionException failure = assertThrows(ExecutionException.class, () -> lit.get(10, TimeUnit.SECONDS));
        assertSame(FAILURE, failure.getCause());
    }

    private static List<Integer> distinctBytes(byte[] data) {
        return IntStream.range(0, data.length).map(index -> data[index]).distinct().boxed().toList();
    }

    private record Parallel(LightEmission emission) implements LightOracle.Engine<FireflyLightEngine> {
        @Override
        public FireflyLightEngine create(LightChunkGetter world, boolean sky) {
            return new FireflyLightEngine(world, null, sky, null, null, this.emission, EXECUTOR);
        }

        @Override
        public void settle(FireflyLightEngine engine) {
            IntStream.range(0, TestWorld.SIZE * TestWorld.SIZE)
                .mapToObj(chunk -> engine.waitForPendingTasks(chunk / TestWorld.SIZE, chunk % TestWorld.SIZE))
                .toList()
                .forEach(CompletableFuture::join);
        }
    }
}
