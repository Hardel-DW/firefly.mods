package fr.hardel.firefly.engine;

import fr.hardel.firefly.server.FireflyLightEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

record Shuffled(long order) implements LightOracle.Engine<FireflyLightEngine> {
    private static final Parallel PARALLEL = new Parallel(LightEmission.VANILLA);

    @Override
    public FireflyLightEngine create(LightChunkGetter world, boolean sky) {
        return PARALLEL.create(world, sky);
    }

    @Override
    public void light(TestWorld world, FireflyLightEngine engine) {
        RandomSource random = RandomSource.create(this.order);
        List<ProtoChunk> chunks = world.chunks();
        boolean[] initialized = new boolean[chunks.size()];
        List<Integer> uninitialized = new ArrayList<>(IntStream.range(0, chunks.size()).boxed().toList());
        List<Integer> unlit = new ArrayList<>(uninitialized);
        while (!unlit.isEmpty()) {
            List<Integer> ready = unlit.stream().filter(index -> Parallel.neighborsInitialized(chunks.get(index).getPos(), initialized)).toList();
            boolean initialize = ready.isEmpty() || !uninitialized.isEmpty() && random.nextBoolean();
            if (initialize) {
                int index = uninitialized.remove(random.nextInt(uninitialized.size()));
                engine.initializeLightAhead(chunks.get(index), false);
                initialized[index] = true;
            }

            if (!initialize) {
                int index = ready.get(random.nextInt(ready.size()));
                ProtoChunk chunk = chunks.get(index);
                engine.lightChunk(chunk, false);
                chunk.setPersistedStatus(ChunkStatus.LIGHT);
                chunk.setLightEngine(engine);
                unlit.remove(Integer.valueOf(index));
            }
        }

        settle(engine);
    }

    @Override
    public void settle(FireflyLightEngine engine) {
        PARALLEL.settle(engine);
    }
}
