package fr.hardel.firefly.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fr.hardel.firefly.MinecraftBootstrap;
import fr.hardel.firefly.server.FireflyLightEngine;
import fr.hardel.firefly.server.LightReader;
import fr.hardel.firefly.storage.Nibbles;
import fr.hardel.firefly.storage.PendingLight;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("mod")
@ExtendWith(MinecraftBootstrap.class)
class LoadedChunkTest {
    private static final int CENTER = TestWorld.SIZE / 2;
    private static final ChunkPos LOADED = new ChunkPos(CENTER, CENTER);
    private static final BlockPos BORDER = new BlockPos(CENTER * 16, 20, CENTER * 16 + 8);

    @Test
    void aChunkSavedBeforeItsLightIsLitFromItsBlocks() {
        TestWorld world = new TestWorld(0, -16, 64, TestWorld.PALETTE);
        Held held = new Held();
        FireflyLightEngine engine = held.create(world, true);
        world.light(engine, held);
        ProtoChunk unloaded = world.unload(LOADED);
        engine.unload(unloaded);
        held.settle(engine);

        ProtoChunk loaded = world.load(unloaded, ChunkStatus.INITIALIZE_LIGHT, false);
        engine.readFromDisk(loaded, dark(engine));
        engine.initializeLightAhead(loaded, false);
        engine.lightChunk(loaded, false);
        held.settle(engine);

        assertEquals(List.of(), LightOracle.read("saved before its light", lightFromZero(new TestWorld(0, -16, 64, TestWorld.PALETTE)), engine));
    }

    @Test
    void aChunkLoadedAgainTakesTheLightItsNeighborGainedWhileItWasAway() {
        TestWorld.Edit glowstone = new TestWorld.Edit(BORDER.west(), Blocks.GLOWSTONE.defaultBlockState());
        TestWorld world = pocket();
        Held held = new Held();
        FireflyLightEngine engine = held.create(world, true);
        world.light(engine, held);
        ProtoChunk unloaded = world.unload(LOADED);
        engine.unload(unloaded);
        world.set(glowstone);
        held.settle(engine);

        ProtoChunk loaded = world.load(unloaded, ChunkStatus.LIGHT, true);
        engine.readFromDisk(loaded, saved(engine, unloaded));
        loaded.setLightEngine(engine);
        engine.initializeLightAhead(loaded, true);
        engine.lightChunk(loaded, true);
        held.settle(engine);

        TestWorld reference = pocket();
        reference.set(glowstone);
        assertEquals(List.of(), LightOracle.read("loaded again", lightFromZero(reference), engine));
    }

    @Test
    void aSectionTheSaveLeavesOutAsDarkStaysDark() {
        TestWorld world = flat();
        Held held = new Held();
        FireflyLightEngine before = held.create(world, true);
        world.light(before, held);
        List<PendingLight> saved = world.chunks().stream().map(chunk -> saved(before, chunk)).toList();
        List<ProtoChunk> unloaded = world.chunks().stream().map(chunk -> world.unload(chunk.getPos())).toList();

        FireflyLightEngine after = held.create(world, true);
        List<ProtoChunk> loaded = unloaded.stream().map(chunk -> world.load(chunk, ChunkStatus.FULL, true)).toList();
        for (int index = 0; index < loaded.size(); index++) {
            after.readFromDisk(loaded.get(index), saved.get(index));
            loaded.get(index).setLightEngine(after);
            after.initializeLightAhead(loaded.get(index), true);
        }

        loaded.forEach(chunk -> after.lightChunk(chunk, true));
        held.settle(after);

        assertEquals(List.of(), LightOracle.read("loaded again", lightFromZero(flat()), after));
    }

    private static TestWorld flat() {
        TestWorld world = new TestWorld(0, -16, 64, TestWorld.PALETTE);
        BlockPos.betweenClosed(0, world.minBlockY(), 0, TestWorld.SIZE * 16 - 1, world.maxBlockY(), TestWorld.SIZE * 16 - 1).forEach(pos -> world.setUnseen(
            new TestWorld.Edit(pos.immutable(), pos.getY() < 16 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState())));
        return world;
    }

    private static TestWorld pocket() {
        TestWorld world = new TestWorld(0, -16, 64, TestWorld.PALETTE);
        BlockPos.betweenClosed(BORDER.offset(-4, -4, -4), BORDER.offset(4, 4, 4)).forEach(pos -> world.setUnseen(new TestWorld.Edit(pos.immutable(), Blocks.AIR.defaultBlockState())));
        return world;
    }

    private static PendingLight dark(FireflyLightEngine engine) {
        PendingLight saved = new PendingLight(engine.getLightSectionCount());
        for (LightLayer layer : LightLayer.values()) {
            for (int section = 0; section < engine.getLightSectionCount(); section++) {
                saved.put(layer, section, Nibbles.ZERO);
            }
        }

        return saved;
    }

    private static PendingLight saved(FireflyLightEngine engine, ProtoChunk chunk) {
        PendingLight saved = new PendingLight(engine.getLightSectionCount());
        saved.retain(true);
        for (LightLayer layer : LightLayer.values()) {
            LightReader reader = (LightReader) engine.getLayerListener(layer);
            for (int section = 0; section < engine.getLightSectionCount(); section++) {
                DataLayer data = reader.getDataLayerData(chunk, SectionPos.of(chunk.getPos(), engine.getMinLightSection() + section));
                if (data != null && !data.isEmpty()) {
                    saved.put(layer, section, Nibbles.of(data));
                }
            }
        }

        return saved;
    }

    private static LevelLightEngine lightFromZero(TestWorld world) {
        LevelLightEngine vanilla = LightOracle.VANILLA.create(world, true);
        world.light(vanilla, LightOracle.VANILLA);
        return vanilla;
    }
}
