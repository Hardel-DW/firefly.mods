package fr.hardel.firefly.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fr.hardel.firefly.MinecraftBootstrap;
import fr.hardel.firefly.server.FireflyLightEngine;
import fr.hardel.firefly.server.LightReader;
import fr.hardel.firefly.storage.ChunkLight;
import fr.hardel.firefly.storage.LightSections;
import fr.hardel.firefly.storage.Nibbles;
import fr.hardel.firefly.storage.PendingLight;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("mod")
@ExtendWith(MinecraftBootstrap.class)
class LoadedChunkTest {
    private static final int CENTER = TestWorld.SIZE / 2;
    private static final ChunkPos LOADED = new ChunkPos(CENTER, CENTER);
    private static final BlockPos BORDER = new BlockPos(CENTER * 16, 20, CENTER * 16 + 8);
    private static final ChunkPos ISLAND_CHUNK = new ChunkPos(CENTER + 1, CENTER);
    private static final ChunkPos BESIDE = new ChunkPos(CENTER - 1, CENTER);
    private static final BlockPos ISLAND = ISLAND_CHUNK.getMiddleBlockPosition(24);
    private static final int RELOADED_WORLDS = 6;
    private static final int RELOADS = 8;
    private static final int EDITS_WHILE_AWAY = 40;
    private static final int BORDER_DEPTH = 3;

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

        assertEquals(List.of(), LightOracle.read("saved before its light", lightFromZero(new TestWorld(0, -16, 64, TestWorld.PALETTE), true), engine));
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
        assertEquals(List.of(), LightOracle.read("loaded again", lightFromZero(reference, true), engine));
    }

    @Test
    void aChunkLoadedAgainBesideUnchangedNeighborsLeavesTheirLightUntouched() {
        TestWorld world = new TestWorld(0, -16, 64, TestWorld.PALETTE);
        Held held = new Held();
        FireflyLightEngine engine = held.create(world, true);
        world.light(engine, held);
        ProtoChunk unloaded = world.unload(LOADED);
        PendingLight saved = saved(engine, unloaded);
        engine.unload(unloaded);
        held.settle(engine);
        LightLog log = new LightLog(engine, TestWorld.SIZE);
        world.watch(log);

        reload(world, engine, held, unloaded, saved);

        assertEquals(List.of(), log.notifiedUnchanged());
    }

    @Test
    void aChunkLoadedAgainSharesItsUniformSections() {
        TestWorld world = new TestWorld(0, -16, 64, TestWorld.PALETTE);
        Held held = new Held();
        FireflyLightEngine engine = held.create(world, true);
        world.light(engine, held);
        ProtoChunk unloaded = world.unload(LOADED);
        PendingLight saved = saved(engine, unloaded);
        engine.unload(unloaded);
        held.settle(engine);

        ProtoChunk loaded = reload(world, engine, held, unloaded, saved);

        for (LightLayer layer : LightLayer.values()) {
            LightSections sections = ((ChunkLight) loaded).firefly$light().sections(layer);
            assertEquals(List.of(), IntStream.range(0, sections.count()).filter(section -> unshared(sections.get(section))).boxed().toList(), layer.name());
        }
    }

    @Test
    void chunksLoadedAgainAfterTheirNeighborsChangedLightLikeLightFromZero() {
        List<String> differences = LongStream.range(0, RELOADED_WORLDS).boxed()
            .flatMap(seed -> Stream.of(true, false).flatMap(sky -> reloadedDifferences(seed, sky).stream()))
            .toList();

        assertEquals(List.of(), differences);
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

        assertEquals(List.of(), LightOracle.read("loaded again", lightFromZero(flat(), true), after));
    }

    @Test
    void aSectionOnlyANeighborStoresBeforeTheChunkStartsTakesTheSkyOfItsColumn() {
        TestWorld world = island();
        Held held = new Held();
        List<Saved> saves = savedWhileTheIslandWasAway(world, held);
        FireflyLightEngine engine = held.create(world, true);
        List<ProtoChunk> loaded = saves.stream().map(save -> save.readFromDisk(world, engine)).toList();
        loaded.stream().filter(chunk -> !chunk.getPos().equals(LOADED)).forEach(chunk -> engine.initializeLightAhead(chunk, true));
        engine.lightChunk(world.chunk(ISLAND), true);
        held.settle(engine);

        engine.initializeLightAhead(world.chunk(LOADED.getWorldPosition()), true);
        loaded.forEach(chunk -> engine.lightChunk(chunk, true));
        held.settle(engine);

        assertEquals(List.of(), LightOracle.read("stored before the chunk starts", lightFromZero(island(), true), engine));
    }

    @Test
    void aSectionOnlyANeighborStoresBeforeTheSaveIsAppliedTakesTheSkyOfItsColumn() {
        TestWorld world = island();
        Held held = new Held();
        List<Saved> saves = savedWhileTheIslandWasAway(world, held);
        FireflyLightEngine engine = held.create(world, true);
        List<ProtoChunk> loaded = saves.stream().filter(save -> !save.chunk().getPos().equals(ISLAND_CHUNK)).map(save -> save.readFromDisk(world, engine)).toList();
        loaded.forEach(chunk -> engine.initializeLightAhead(chunk, true));
        engine.lightChunk(world.chunk(BESIDE.getWorldPosition()), true);
        held.settle(engine);

        ProtoChunk island = saves.stream().filter(save -> save.chunk().getPos().equals(ISLAND_CHUNK)).findFirst().orElseThrow().readFromDisk(world, engine);
        engine.initializeLightAhead(island, true);
        engine.lightChunk(island, true);
        held.settle(engine);

        loaded.forEach(chunk -> engine.lightChunk(chunk, true));
        held.settle(engine);

        assertEquals(List.of(), LightOracle.read("stored before the save is applied", lightFromZero(island(), true), engine));
    }

    private static List<String> reloadedDifferences(long seed, boolean sky) {
        TestWorld world = new TestWorld(seed, -16, 64, TestWorld.PALETTE);
        TestWorld reference = new TestWorld(seed, -16, 64, TestWorld.PALETTE);
        Held held = new Held();
        FireflyLightEngine engine = held.create(world, sky);
        world.light(engine, held);
        RandomSource random = RandomSource.create(seed);
        for (int round = 0; round < RELOADS; round++) {
            ProtoChunk unloaded = world.unload(new ChunkPos(1 + random.nextInt(TestWorld.SIZE - 2), 1 + random.nextInt(TestWorld.SIZE - 2)));
            PendingLight saved = random.nextBoolean() ? saved(engine, unloaded) : null;
            engine.unload(unloaded);
            held.settle(engine);
            for (int edit = 0; edit < EDITS_WHILE_AWAY; edit++) {
                TestWorld.Edit change = besideTheBorder(random, unloaded.getPos());
                world.set(change);
                reference.setUnseen(change);
            }

            held.settle(engine);
            reload(world, engine, held, unloaded, saved);
        }

        return LightOracle.read("seed %d, sky %s, reloaded".formatted(seed, sky), lightFromZero(reference, sky), engine);
    }

    private static ProtoChunk reload(TestWorld world, FireflyLightEngine engine, Held held, ProtoChunk unloaded, @Nullable PendingLight saved) {
        boolean lighted = saved != null;
        ProtoChunk loaded = world.load(unloaded, lighted ? ChunkStatus.LIGHT : ChunkStatus.INITIALIZE_LIGHT, lighted);
        engine.readFromDisk(loaded, saved);
        engine.initializeLightAhead(loaded, lighted);
        engine.lightChunk(loaded, lighted);
        held.settle(engine);
        loaded.setPersistedStatus(ChunkStatus.LIGHT);
        loaded.setLightEngine(engine);
        return loaded;
    }

    private static boolean unshared(byte @Nullable [] data) {
        return data != null && !Nibbles.shared(data) && (Arrays.equals(data, Nibbles.ZERO) || Arrays.equals(data, Nibbles.FULL));
    }

    private static TestWorld.Edit besideTheBorder(RandomSource random, ChunkPos away) {
        Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int along = random.nextInt(16);
        int depth = random.nextInt(BORDER_DEPTH);
        int x = side.getStepX() == 0 ? along : side.getStepX() > 0 ? 16 + depth : -1 - depth;
        int z = side.getStepZ() == 0 ? along : side.getStepZ() > 0 ? 16 + depth : -1 - depth;
        BlockPos pos = new BlockPos(away.getMinBlockX() + x, -16 + random.nextInt(64), away.getMinBlockZ() + z);
        return new TestWorld.Edit(pos, TestWorld.PALETTE.get(random.nextInt(TestWorld.PALETTE.size())));
    }

    private static List<Saved> savedWhileTheIslandWasAway(TestWorld world, Held held) {
        List<ProtoChunk> chunks = world.chunks();
        FireflyLightEngine engine = held.create(world, true);
        world.light(engine, held);
        PendingLight island = saved(engine, world.chunk(ISLAND));
        engine.unload(world.unload(ISLAND_CHUNK));
        held.settle(engine);

        List<Saved> saves = chunks.stream().map(chunk -> new Saved(chunk, chunk.getPos().equals(ISLAND_CHUNK) ? island : saved(engine, chunk))).toList();
        chunks.forEach(chunk -> world.unload(chunk.getPos()));
        return saves;
    }

    private static TestWorld flat() {
        return filled(pos -> pos.getY() < 16);
    }

    private static TestWorld island() {
        return filled(ISLAND::equals);
    }

    private static TestWorld filled(Predicate<BlockPos> stone) {
        TestWorld world = new TestWorld(0, -16, 64, TestWorld.PALETTE);
        BlockPos.betweenClosed(0, world.minBlockY(), 0, TestWorld.SIZE * 16 - 1, world.maxBlockY(), TestWorld.SIZE * 16 - 1).forEach(pos -> world.setUnseen(
            new TestWorld.Edit(pos.immutable(), stone.test(pos) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState())));
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

    static PendingLight saved(FireflyLightEngine engine, ProtoChunk chunk) {
        PendingLight saved = new PendingLight(engine.getLightSectionCount());
        saved.retain(true);
        for (LightLayer layer : LightLayer.values()) {
            for (int section = 0; section < engine.getLightSectionCount(); section++) {
                SectionPos pos = SectionPos.of(chunk.getPos(), engine.getMinLightSection() + section);
                DataLayer data = engine.getLayerListener(layer) instanceof LightReader reader ? reader.getDataLayerData(chunk, pos) : null;
                if (data != null && !data.isEmpty()) {
                    saved.put(layer, section, Nibbles.of(new DataLayer(data.copy().getData())));
                }
            }
        }

        return saved;
    }

    private static LevelLightEngine lightFromZero(TestWorld world, boolean sky) {
        LevelLightEngine vanilla = LightOracle.VANILLA.create(world, sky);
        world.light(vanilla, LightOracle.VANILLA);
        return vanilla;
    }

    private record Saved(ProtoChunk chunk, PendingLight light) {
        ProtoChunk readFromDisk(TestWorld world, FireflyLightEngine engine) {
            ProtoChunk loaded = world.load(this.chunk, ChunkStatus.FULL, true);
            engine.readFromDisk(loaded, this.light);
            loaded.setLightEngine(engine);
            return loaded;
        }
    }
}
