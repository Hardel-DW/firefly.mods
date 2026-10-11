package fr.hardel.firefly.engine;

import fr.hardel.firefly.schedule.LightExecutor;
import fr.hardel.firefly.server.FireflyLightEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

final class Unordered implements LightOracle.Engine<FireflyLightEngine>, LightExecutor {
    private static final int DRAINS = 4;
    private static final int DRAINED = 8;

    private final List<Runnable> tasks = new ArrayList<>();
    private final RandomSource random;

    Unordered(long order) {
        this.random = RandomSource.create(order);
    }

    @Override
    public void execute(LightChunkGetter level, int chunkX, int chunkZ, Runnable task) {
        this.tasks.add(task);
    }

    @Override
    public FireflyLightEngine create(LightChunkGetter world, boolean sky) {
        return new FireflyLightEngine(world, null, sky, null, null, LightEmission.VANILLA, this);
    }

    @Override
    public void light(TestWorld world, FireflyLightEngine engine) {
        List<ProtoChunk> chunks = world.chunks();
        boolean[] initialized = new boolean[chunks.size()];
        List<Integer> uninitialized = new ArrayList<>(IntStream.range(0, chunks.size()).boxed().toList());
        List<Integer> unlit = new ArrayList<>(uninitialized);
        while (!unlit.isEmpty()) {
            List<Integer> ready = unlit.stream().filter(index -> Parallel.neighborsInitialized(chunks.get(index).getPos(), initialized)).toList();
            boolean initialize = ready.isEmpty() || !uninitialized.isEmpty() && this.random.nextBoolean();
            if (initialize) {
                int index = uninitialized.remove(this.random.nextInt(uninitialized.size()));
                engine.initializeLightAhead(chunks.get(index), false);
                initialized[index] = true;
            }

            if (!initialize) {
                int index = ready.get(this.random.nextInt(ready.size()));
                ProtoChunk chunk = chunks.get(index);
                engine.lightChunk(chunk, false);
                chunk.setPersistedStatus(ChunkStatus.LIGHT);
                chunk.setLightEngine(engine);
                unlit.remove(Integer.valueOf(index));
            }

            if (this.random.nextInt(DRAINS) == 0) {
                run(this.random.nextInt(DRAINED));
            }
        }

        settle(engine);
    }

    @Override
    public void settle(FireflyLightEngine engine) {
        engine.tryScheduleUpdate();
        run(Integer.MAX_VALUE);
    }

    private void run(int count) {
        for (int ran = 0; ran < count && !this.tasks.isEmpty(); ran++) {
            this.tasks.remove(this.random.nextInt(this.tasks.size())).run();
        }
    }
}
