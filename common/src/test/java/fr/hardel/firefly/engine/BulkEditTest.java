package fr.hardel.firefly.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.hardel.firefly.MinecraftBootstrap;
import fr.hardel.firefly.server.FireflyLightEngine;
import fr.hardel.firefly.storage.ChunkLight;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("mod")
@ExtendWith(MinecraftBootstrap.class)
class BulkEditTest {
    private static final int MIN_Y = -16;
    private static final int HEIGHT = 64;
    private static final int LOW = 36;
    private static final int HIGH = 75;
    private static final int BOTTOM = -4;
    private static final int TOP = 19;
    private static final int PASTE_X = 8;
    private static final int PASTE_Y = 24;
    private static final int QUEUED = 3;
    private static final long[] RANDOM_SEEDS = {0, 1, 2, 3, 4};
    private static final int RANDOM_MIN_Y = 0;
    private static final int RANDOM_HEIGHT = 48;
    private static final int RANDOM_ROUNDS = 24;

    @Test
    void vanillaLightsACutAndPasteLikeLightFromZero() {
        TestWorld world = world();
        LevelLightEngine engine = LightOracle.VANILLA.create(world, true);
        world.light(engine, LightOracle.VANILLA);

        cutAndPaste(world, engine).forEach(engine::checkBlock);
        engine.runLightUpdates();

        assertEquals(List.of(), LightOracle.read("vanilla", lightFromZero(), engine));
    }

    @Test
    void aCutAndPasteCheckedThroughTheEngineLightsLikeLightFromZero() {
        TestWorld world = world();
        Held held = new Held();
        FireflyLightEngine engine = held.create(world, true);
        world.light(engine, held);

        cutAndPaste(world, engine).forEach(engine::checkBlock);
        held.settle(engine);

        assertEquals(List.of(), LightOracle.read("through the engine", lightFromZero(), engine));
    }

    @Test
    void aCutAndPasteCheckedFromAVanillaTaskLightsLikeLightFromZero() throws ReflectiveOperationException {
        TestWorld world = world();
        Held held = new Held();
        ConsecutiveExecutor light = VanillaQueue.executor();
        FireflyLightEngine engine = new FireflyLightEngine(world, null, true, light, VanillaQueue.dispatcher(light), LightEmission.VANILLA, held);
        world.light(engine, held);
        VanillaQueue queue = new VanillaQueue();
        VanillaParent parent = new VanillaParent(engine);

        for (BlockPos pos : cutAndPaste(world, engine)) {
            queue.addPreUpdate(engine, pos.getX() >> 4, pos.getZ() >> 4, () -> parent.call("checkBlock", void.class, List.of(BlockPos.class), pos));
        }

        held.settle(engine);

        assertEquals(List.of(), LightOracle.read("from a vanilla task", lightFromZero(), engine));
    }

    @Test
    void aCheckFromAVanillaTaskIsSubmittedByTheRunOfItsQueue() throws ReflectiveOperationException {
        TestWorld world = world();
        Held held = new Held();
        ConsecutiveExecutor light = VanillaQueue.executor();
        FireflyLightEngine engine = new FireflyLightEngine(world, null, true, light, VanillaQueue.dispatcher(light), LightEmission.VANILLA, held);
        world.light(engine, held);
        VanillaParent parent = new VanillaParent(engine);
        BlockPos pos = new BlockPos(LOW, 0, LOW);

        new VanillaQueue().addPreUpdate(engine, LOW >> 4, LOW >> 4, () -> parent.call("checkBlock", void.class, List.of(BlockPos.class), pos));
        engine.tryScheduleUpdate();

        assertEquals(1, held.waiting());
    }

    @Test
    void postUpdatesWaitForTheLightAndKeepTheirOrder() throws ReflectiveOperationException {
        TestWorld world = world();
        Held held = new Held();
        ConsecutiveExecutor light = VanillaQueue.executor();
        FireflyLightEngine engine = new FireflyLightEngine(world, null, true, light, VanillaQueue.dispatcher(light), LightEmission.VANILLA, held);
        world.light(engine, held);
        VanillaQueue queue = new VanillaQueue();
        List<String> ran = new ArrayList<>();

        engine.checkBlock(new BlockPos(LOW, 0, LOW));
        queue.addPostUpdate(engine, LOW >> 4, LOW >> 4, () -> ran.add("checked chunk"));
        queue.addPostUpdate(engine, TestWorld.SIZE - 1, TestWorld.SIZE - 1, () -> ran.add("idle chunk"));
        engine.tryScheduleUpdate();
        List<String> ranBeforeTheLight = List.copyOf(ran);
        held.runUntil(() -> held.waiting() == 0);

        assertEquals(List.of(), ranBeforeTheLight);
        assertEquals(List.of("checked chunk", "idle chunk"), ran);
    }

    @Test
    void randomBoxesInTheWrongOrderLightLikeLightFromZero() {
        List<String> differences = new ArrayList<>();
        for (long seed : RANDOM_SEEDS) {
            differences.addAll(randomBoxes(seed));
        }

        assertEquals(List.of(), differences);
    }

    @Test
    void aSectionStatusAskedThroughTheVanillaParentStoresTheSection() {
        TestWorld world = world();
        BlockPos.betweenClosed(0, 16, 0, TestWorld.SIZE * 16 - 1, world.maxBlockY(), TestWorld.SIZE * 16 - 1).forEach(pos ->
            world.setUnseen(new TestWorld.Edit(pos.immutable(), Blocks.AIR.defaultBlockState())));
        Held held = new Held();
        FireflyLightEngine engine = held.create(world, true);
        world.light(engine, held);
        BlockPos stone = new BlockPos(LOW, 32, LOW);
        assertNull(engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(stone)));
        world.setUnseen(new TestWorld.Edit(stone, Blocks.STONE.defaultBlockState()));

        new VanillaParent(engine).call("updateSectionStatus", void.class, List.of(SectionPos.class, boolean.class), SectionPos.of(stone), false);
        held.settle(engine);

        assertNotNull(engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(stone)));
    }

    @Test
    void aLightOffAskedThroughTheVanillaParentTurnsTheColumnOff() {
        TestWorld world = world();
        Held held = new Held();
        FireflyLightEngine engine = held.create(world, true);
        world.light(engine, held);
        ChunkPos chunk = new ChunkPos(LOW >> 4, LOW >> 4);

        new VanillaParent(engine).call("setLightEnabled", void.class, List.of(ChunkPos.class, boolean.class), chunk, false);
        held.settle(engine);

        assertFalse(engine.lightOnInColumn(SectionPos.asLong(chunk.x(), 0, chunk.z())));
    }

    @Test
    void aPropagationAskedThroughTheVanillaParentLightsTheSources() {
        TestWorld world = world();
        BlockPos glowstone = new BlockPos(LOW, 0, LOW);
        world.setUnseen(new TestWorld.Edit(glowstone, Blocks.GLOWSTONE.defaultBlockState()));
        Held held = new Held();
        FireflyLightEngine engine = held.create(world, true);
        ProtoChunk chunk = world.chunk(glowstone);
        engine.updateSectionStatus(glowstone, false);
        held.settle(engine);

        new VanillaParent(engine).call("propagateLightSources", void.class, List.of(ChunkPos.class), chunk.getPos());
        held.settle(engine);

        assertEquals(Blocks.GLOWSTONE.defaultBlockState().getLightEmission(), engine.getLayerListener(LightLayer.BLOCK).getLightValue(glowstone));
    }

    @Test
    void lightQueuedThroughTheVanillaParentIsKeptAndRetained() {
        TestWorld world = world();
        BlockPos pos = new BlockPos(LOW, 0, LOW);
        ProtoChunk chunk = world.chunk(pos);
        FireflyLightEngine engine = new Held().create(world, true);
        VanillaParent parent = new VanillaParent(engine);

        parent.call("retainData", void.class, List.of(ChunkPos.class, boolean.class), chunk.getPos(), true);
        parent.call("queueSectionData", void.class, List.of(LightLayer.class, SectionPos.class, DataLayer.class), LightLayer.BLOCK, SectionPos.of(pos), new DataLayer(QUEUED));

        assertEquals(QUEUED, engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(pos)).get(0, 0, 0));
        assertTrue(((ChunkLight) chunk).firefly$light().queued().retained());
    }

    private static List<String> randomBoxes(long seed) {
        TestWorld world = new TestWorld(seed, RANDOM_MIN_Y, RANDOM_HEIGHT, TestWorld.PALETTE);
        Unordered unordered = new Unordered(seed + 1);
        FireflyLightEngine engine = unordered.create(world, true);
        unordered.light(world, engine);
        RandomSource random = RandomSource.create(seed * 31 + RANDOM_MIN_Y);
        List<TestWorld.Edit> edits = new ArrayList<>();
        for (int round = 0; round < RANDOM_ROUNDS; round++) {
            List<TestWorld.Edit> box = world.randomBox(random);
            edits.addAll(box);
            box.forEach(world::set);
            unordered.settle(engine);
        }

        TestWorld reference = new TestWorld(seed, RANDOM_MIN_Y, RANDOM_HEIGHT, TestWorld.PALETTE);
        edits.forEach(reference::setUnseen);
        LevelLightEngine fromZero = LightOracle.VANILLA.create(reference, true);
        reference.light(fromZero, LightOracle.VANILLA);
        return LightOracle.read("seed %d".formatted(seed), fromZero, engine);
    }

    private static List<BlockPos> cutAndPaste(TestWorld world, LevelLightEngine engine) {
        List<TestWorld.Edit> pasted = new ArrayList<>();
        List<TestWorld.Edit> cut = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(LOW, BOTTOM, LOW, HIGH, TOP, HIGH)) {
            pasted.add(new TestWorld.Edit(pos.offset(PASTE_X, PASTE_Y, 0), world.getBlockState(pos)));
            cut.add(new TestWorld.Edit(pos.immutable(), Blocks.AIR.defaultBlockState()));
        }

        AxiomBuffer buffer = new AxiomBuffer(world, engine, true);
        return Stream.of(buffer.write(cut), buffer.write(pasted)).flatMap(changed -> changed.values().stream()).flatMap(List::stream).toList();
    }

    private static LevelLightEngine lightFromZero() {
        TestWorld reference = world();
        cutAndPaste(reference, LevelLightEngine.EMPTY);
        LevelLightEngine engine = LightOracle.VANILLA.create(reference, true);
        reference.light(engine, LightOracle.VANILLA);
        return engine;
    }

    private static TestWorld world() {
        return new TestWorld(0, MIN_Y, HEIGHT, TestWorld.PALETTE);
    }
}
