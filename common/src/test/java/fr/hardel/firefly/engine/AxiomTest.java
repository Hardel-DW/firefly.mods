package fr.hardel.firefly.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fr.hardel.firefly.MinecraftBootstrap;
import fr.hardel.firefly.server.FireflyLightEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("mod")
@ExtendWith(MinecraftBootstrap.class)
class AxiomTest {
    private static final int MIN_Y = -16;
    private static final int HEIGHT = 64;
    private static final long[] SEEDS = {0, 1, 3, 5};
    private static final BlockPos MELT_CENTER = new BlockPos(48, 6, 48);
    private static final int MELT_RADIUS = 6;

    @Test
    void theLightOfAMeltIsRightWhenItsFuturesComplete() throws Exception {
        List<String> differences = new ArrayList<>();
        for (long seed : SEEDS) {
            differences.addAll(meltThroughTheVanillaQueue(seed));
        }

        assertEquals(List.of(), differences);
    }

    private static List<String> meltThroughTheVanillaQueue(long seed) throws Exception {
        TestWorld world = world(seed);
        ConsecutiveExecutor light = VanillaQueue.executor();
        FireflyLightEngine engine = new FireflyLightEngine(world, null, true, light, VanillaQueue.dispatcher(light), LightEmission.VANILLA, Parallel.EXECUTOR);
        new Parallel(LightEmission.VANILLA).light(world, engine);
        List<TestWorld.Edit> edits = melt(world);
        Map<SectionPos, List<BlockPos>> changed = new AxiomBuffer(world, engine, true).write(edits);
        LevelLightEngine fromZero = lightFromZero(seed, edits);
        VanillaQueue queue = new VanillaQueue();
        VanillaParent parent = new VanillaParent(engine);

        changed.forEach((section, positions) -> queue.addPreUpdate(engine, section.x(), section.z(),
            () -> positions.forEach(pos -> parent.call("checkBlock", void.class, List.of(BlockPos.class), pos))));
        CompletableFuture<?>[] waited = changed.keySet().stream().map(SectionPos::chunk).distinct()
            .map(chunk -> queue.waitForPostUpdate(engine, chunk.x(), chunk.z()))
            .toArray(CompletableFuture[]::new);
        CompletableFuture<List<String>> read = CompletableFuture.allOf(waited)
            .thenApply(_ -> LightOracle.read("seed %d, when the futures of the melt complete".formatted(seed), fromZero, engine));
        engine.tryScheduleUpdate();

        return read.get(30, TimeUnit.SECONDS);
    }

    private static List<TestWorld.Edit> melt(TestWorld world) {
        List<TestWorld.Edit> edits = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(MELT_CENTER.offset(-MELT_RADIUS, -MELT_RADIUS, -MELT_RADIUS), MELT_CENTER.offset(MELT_RADIUS, MELT_RADIUS, MELT_RADIUS))) {
            if (pos.distSqr(MELT_CENTER) <= MELT_RADIUS * MELT_RADIUS && !world.getBlockState(pos).isAir()) {
                edits.add(new TestWorld.Edit(pos.immutable(), Blocks.AIR.defaultBlockState()));
            }
        }

        return edits;
    }

    private static LevelLightEngine lightFromZero(long seed, List<TestWorld.Edit> edits) {
        TestWorld reference = world(seed);
        edits.forEach(reference::setUnseen);
        LevelLightEngine engine = LightOracle.VANILLA.create(reference, true);
        reference.light(engine, LightOracle.VANILLA);
        return engine;
    }

    private static TestWorld world(long seed) {
        return new TestWorld(seed, MIN_Y, HEIGHT, TestWorld.PALETTE);
    }
}
