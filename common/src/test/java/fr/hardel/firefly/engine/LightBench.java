package fr.hardel.firefly.engine;

import com.sun.management.ThreadMXBean;
import fr.hardel.firefly.MinecraftBootstrap;
import fr.hardel.firefly.schedule.LightExecutor;
import fr.hardel.firefly.server.FireflyLightEngine;
import fr.hardel.firefly.storage.PendingLight;
import java.lang.management.ManagementFactory;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("bench")
@ExtendWith(MinecraftBootstrap.class)
class LightBench {
    private static final List<BlockState> CARVED = List.of(Blocks.AIR.defaultBlockState());
    private static final BlockState SOURCE = Blocks.GLOWSTONE.defaultBlockState();
    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;
    private static final int SOURCES_PER_CHUNK = 2;
    private static final int WORLDS = 8;
    private static final int WARMUP_ROUNDS = 20;
    private static final int MEASURED_ROUNDS = 9;
    private static final LightExecutor INLINE = (_, _, _, task) -> task.run();
    private static final ThreadMXBean THREADS = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    @Test
    void timesTheLightOfTheSameWorldsOnOneThread() {
        IntStream.range(0, WARMUP_ROUNDS).forEach(_ -> round());
        List<Round> rounds = IntStream.range(0, MEASURED_ROUNDS).mapToObj(_ -> round()).sorted(Comparator.comparingDouble(Round::perChunk)).toList();
        rounds.forEach(round -> System.out.println("light bench round: %s".formatted(round)));
        System.out.println("light bench median: %s".formatted(rounds.get(MEASURED_ROUNDS / 2)));
        System.out.println("light bench reload median: %.1f us per chunk".formatted(rounds.stream().mapToDouble(Round::reload).sorted().toArray()[MEASURED_ROUNDS / 2]));
    }

    private static Round round() {
        long initialization = 0;
        long lighting = 0;
        long reload = 0;
        long allocated = 0;
        int chunks = 0;
        for (int seed = 0; seed < WORLDS; seed++) {
            TestWorld world = world(seed);
            FireflyLightEngine engine = new FireflyLightEngine(world, null, true, null, null, LightEmission.VANILLA, INLINE);
            long start = System.nanoTime();
            long allocatedBefore = THREADS.getCurrentThreadAllocatedBytes();
            world.chunks().forEach(chunk -> engine.initializeLightAhead(chunk, false));
            long initialized = System.nanoTime();
            world.chunks().forEach(chunk -> engine.lightChunk(chunk, false));
            initialization += initialized - start;
            lighting += System.nanoTime() - initialized;
            allocated += THREADS.getCurrentThreadAllocatedBytes() - allocatedBefore;
            reload += reload(world, engine);
            chunks += world.chunks().size();
        }

        return new Round(initialization / 1000.0 / chunks, lighting / 1000.0 / chunks, reload / 1000.0 / chunks, allocated / 1024.0 / chunks);
    }

    private static long reload(TestWorld world, FireflyLightEngine engine) {
        List<ProtoChunk> unloaded = world.chunks();
        List<PendingLight> saves = unloaded.stream().map(chunk -> LoadedChunkTest.saved(engine, chunk)).toList();
        unloaded.forEach(chunk -> engine.unload(world.unload(chunk.getPos())));
        List<ProtoChunk> loaded = IntStream.range(0, unloaded.size()).mapToObj(index -> {
            ProtoChunk chunk = world.load(unloaded.get(index), ChunkStatus.LIGHT, true);
            engine.readFromDisk(chunk, saves.get(index));
            engine.initializeLightAhead(chunk, true);
            return chunk;
        }).toList();
        long start = System.nanoTime();
        loaded.forEach(chunk -> engine.lightChunk(chunk, true));
        return System.nanoTime() - start;
    }

    private static TestWorld world(int seed) {
        TestWorld world = new TestWorld(seed, MIN_Y, HEIGHT, CARVED);
        RandomSource random = RandomSource.create(seed);
        for (int source = 0; source < TestWorld.SIZE * TestWorld.SIZE * SOURCES_PER_CHUNK; source++) {
            BlockPos pos = new BlockPos(random.nextInt(TestWorld.SIZE * 16), MIN_Y + random.nextInt(HEIGHT / 3), random.nextInt(TestWorld.SIZE * 16));
            world.setUnseen(new TestWorld.Edit(pos, SOURCE));
        }

        return world;
    }

    private record Round(double initialization, double lighting, double reload, double allocated) {
        double perChunk() {
            return this.initialization + this.lighting;
        }

        @Override
        public String toString() {
            return "%.1f us per chunk, initialization %.1f us, light %.1f us, reload %.1f us, allocated %.1f KB".formatted(perChunk(), this.initialization, this.lighting,
                this.reload, this.allocated);
        }
    }
}
