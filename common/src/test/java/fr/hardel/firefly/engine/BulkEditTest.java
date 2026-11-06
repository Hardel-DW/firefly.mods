package fr.hardel.firefly.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.hardel.firefly.MinecraftBootstrap;
import fr.hardel.firefly.server.FireflyLightEngine;
import fr.hardel.firefly.storage.ChunkLight;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
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

    @Test
    void vanillaLightsACutAndPasteLikeLightFromZero() {
        TestWorld world = world();
        LevelLightEngine engine = LightOracle.VANILLA.create(world, true);
        world.light(engine, LightOracle.VANILLA);

        cutAndPaste(world, engine, engine::checkBlock);
        engine.runLightUpdates();

        assertEquals(List.of(), LightOracle.read("vanilla", lightFromZero(), engine));
    }

    @Test
    void aCutAndPasteCheckedThroughTheEngineLightsLikeLightFromZero() {
        TestWorld world = world();
        Held held = new Held();
        FireflyLightEngine engine = held.create(world, true);
        world.light(engine, held);

        cutAndPaste(world, engine, engine::checkBlock);
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
        Parent parent = new Parent(engine);
        List<BlockPos> changed = new ArrayList<>();

        cutAndPaste(world, engine, changed::add);
        for (BlockPos pos : changed) {
            queue.addPreUpdate(engine, pos.getX() >> 4, pos.getZ() >> 4, () -> parent.call("checkBlock", void.class, List.of(BlockPos.class), pos));
        }

        held.settle(engine);
        held.settle(engine);

        assertEquals(List.of(), LightOracle.read("from a vanilla task", lightFromZero(), engine));
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

        new Parent(engine).call("updateSectionStatus", void.class, List.of(SectionPos.class, boolean.class), SectionPos.of(stone), false);
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

        new Parent(engine).call("setLightEnabled", void.class, List.of(ChunkPos.class, boolean.class), chunk, false);
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

        new Parent(engine).call("propagateLightSources", void.class, List.of(ChunkPos.class), chunk.getPos());
        held.settle(engine);

        assertEquals(Blocks.GLOWSTONE.defaultBlockState().getLightEmission(), engine.getLayerListener(LightLayer.BLOCK).getLightValue(glowstone));
    }

    @Test
    void lightQueuedThroughTheVanillaParentIsKeptAndRetained() {
        TestWorld world = world();
        BlockPos pos = new BlockPos(LOW, 0, LOW);
        ProtoChunk chunk = world.chunk(pos);
        FireflyLightEngine engine = new Held().create(world, true);
        Parent parent = new Parent(engine);

        parent.call("retainData", void.class, List.of(ChunkPos.class, boolean.class), chunk.getPos(), true);
        parent.call("queueSectionData", void.class, List.of(LightLayer.class, SectionPos.class, DataLayer.class), LightLayer.BLOCK, SectionPos.of(pos), new DataLayer(QUEUED));

        assertEquals(QUEUED, engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(pos)).get(0, 0, 0));
        assertTrue(((ChunkLight) chunk).firefly$light().queued().retained());
    }

    private static void cutAndPaste(TestWorld world, LevelLightEngine engine, Consumer<BlockPos> check) {
        List<TestWorld.Edit> pasted = new ArrayList<>();
        List<TestWorld.Edit> cut = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(LOW, BOTTOM, LOW, HIGH, TOP, HIGH)) {
            pasted.add(new TestWorld.Edit(pos.offset(PASTE_X, PASTE_Y, 0), world.getBlockState(pos)));
            cut.add(new TestWorld.Edit(pos.immutable(), Blocks.AIR.defaultBlockState()));
        }

        cut.forEach(edit -> writeInSection(world, engine, edit, check));
        pasted.forEach(edit -> writeInSection(world, engine, edit, check));
    }

    private static void writeInSection(TestWorld world, LevelLightEngine engine, TestWorld.Edit edit, Consumer<BlockPos> check) {
        BlockPos pos = edit.pos();
        ProtoChunk chunk = world.chunk(pos);
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
        boolean wasEmpty = section.hasOnlyAir();
        BlockState old = section.setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, edit.state());
        if (wasEmpty != section.hasOnlyAir()) {
            engine.updateSectionStatus(pos, !wasEmpty);
        }

        if (LightEngine.hasDifferentLightProperties(old, edit.state())) {
            chunk.getSkyLightSources().update(chunk, pos.getX() & 15, pos.getY(), pos.getZ() & 15);
            check.accept(pos);
        }
    }

    private static LevelLightEngine lightFromZero() {
        TestWorld reference = world();
        cutAndPaste(reference, LevelLightEngine.EMPTY, _ -> {});
        LevelLightEngine engine = LightOracle.VANILLA.create(reference, true);
        reference.light(engine, LightOracle.VANILLA);
        return engine;
    }

    private static TestWorld world() {
        return new TestWorld(0, MIN_Y, HEIGHT, TestWorld.PALETTE);
    }

    private record Parent(FireflyLightEngine engine) {
        private void call(String method, Class<?> result, List<Class<?>> parameters, Object... arguments) {
            try {
                handle(method, result, parameters).invokeWithArguments(arguments);
            } catch (Throwable failure) {
                throw new AssertionError(failure);
            }
        }

        private MethodHandle handle(String method, Class<?> result, List<Class<?>> parameters) throws ReflectiveOperationException {
            return MethodHandles.privateLookupIn(ThreadedLevelLightEngine.class, MethodHandles.lookup())
                .findSpecial(LevelLightEngine.class, method, MethodType.methodType(result, parameters), ThreadedLevelLightEngine.class)
                .bindTo(this.engine);
        }
    }
}
