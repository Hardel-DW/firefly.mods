package fr.hardel.firefly.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fr.hardel.firefly.MinecraftBootstrap;
import fr.hardel.firefly.server.FireflyLightEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("mod")
@ExtendWith(MinecraftBootstrap.class)
class TaskOrderTest {
    private static final Block STONE = Blocks.STONE;
    private static final Block LAMP = Blocks.GLOWSTONE;
    private static final Block AIR = Blocks.AIR;

    @Test
    void aSectionEmptiedBeforeItsCheckLeavesNoLightBehind() {
        List<TestWorld.Edit> before = List.of(edit(40, 24, 56, STONE), edit(88, 24, 56, STONE), edit(63, 24, 56, LAMP));
        List<TestWorld.Edit> edits = List.of(edit(63, 24, 56, AIR), edit(40, 24, 56, AIR));

        assertEquals(List.of(), inBothOrders(-16, 64, false, before, edits));
    }

    @Test
    void aLampCutWithItsSectionLeavesNoLightBelow() {
        List<TestWorld.Edit> before = new ArrayList<>(List.of(edit(36, 16, 36, LAMP)));
        box(before, 0, -16, 0, TestWorld.SIZE * 16 - 1, -1, TestWorld.SIZE * 16 - 1, STONE);
        List<TestWorld.Edit> edits = List.of(edit(36, 16, 36, AIR), edit(52, -1, 36, AIR));

        assertEquals(List.of(), inBothOrders(-16, 64, true, before, edits));
    }

    @Test
    void aRoofCutWithItsSectionsLetsTheSkyIntoTheNextChunk() {
        List<TestWorld.Edit> before = new ArrayList<>(List.of(edit(40, 24, 56, STONE), edit(88, 24, 56, STONE)));
        box(before, 64, 64, 48, 79, 64, 63, STONE);
        box(before, 48, 24, 48, 63, 24, 63, STONE);
        List<TestWorld.Edit> edits = new ArrayList<>();
        box(edits, 48, 24, 48, 63, 24, 63, AIR);
        edits.add(edit(40, 24, 56, AIR));

        assertEquals(List.of(), inBothOrders(0, 128, true, before, edits));
    }

    private static List<String> inBothOrders(int minY, int height, boolean sky, List<TestWorld.Edit> before, List<TestWorld.Edit> edits) {
        List<String> differences = new ArrayList<>(run("in order", minY, height, sky, before, edits));
        differences.addAll(run("reversed", minY, height, sky, before, edits.reversed()));
        return differences;
    }

    private static List<String> run(String order, int minY, int height, boolean sky, List<TestWorld.Edit> before, List<TestWorld.Edit> edits) {
        TestWorld world = world(minY, height);
        before.forEach(world::setUnseen);
        Held held = new Held();
        FireflyLightEngine engine = held.create(world, sky);
        world.light(engine, held);
        edits.forEach(world::set);
        held.settle(engine);

        TestWorld reference = world(minY, height);
        Stream.concat(before.stream(), edits.stream()).forEach(reference::setUnseen);
        LevelLightEngine fromZero = LightOracle.VANILLA.create(reference, sky);
        reference.light(fromZero, LightOracle.VANILLA);
        return LightOracle.read(order, fromZero, engine);
    }

    private static TestWorld world(int minY, int height) {
        TestWorld world = new TestWorld(0, minY, height, TestWorld.PALETTE);
        BlockPos.betweenClosed(0, minY, 0, TestWorld.SIZE * 16 - 1, world.maxBlockY(), TestWorld.SIZE * 16 - 1)
            .forEach(pos -> world.setUnseen(new TestWorld.Edit(pos.immutable(), AIR.defaultBlockState())));
        return world;
    }

    private static void box(List<TestWorld.Edit> edits, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1).forEach(pos -> edits.add(new TestWorld.Edit(pos.immutable(), block.defaultBlockState())));
    }

    private static TestWorld.Edit edit(int x, int y, int z, Block block) {
        return new TestWorld.Edit(new BlockPos(x, y, z), block.defaultBlockState());
    }
}
