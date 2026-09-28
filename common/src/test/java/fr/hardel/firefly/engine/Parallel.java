package fr.hardel.firefly.engine;

import fr.hardel.firefly.schedule.ReservingExecutor;
import fr.hardel.firefly.server.FireflyLightEngine;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

record Parallel(LightEmission emission) implements LightOracle.Engine<FireflyLightEngine> {
    static final ReservingExecutor EXECUTOR = new ReservingExecutor(4);

    @Override
    public FireflyLightEngine create(LightChunkGetter world, boolean sky) {
        return new FireflyLightEngine(world, null, sky, null, null, this.emission, EXECUTOR);
    }

    @Override
    public void light(TestWorld world, FireflyLightEngine engine) {
        List<ProtoChunk> chunks = world.chunks();
        boolean[] initialized = new boolean[chunks.size()];
        boolean[] lit = new boolean[chunks.size()];
        for (int index = 0; index < chunks.size(); index++) {
            ProtoChunk chunk = chunks.get(index);
            engine.initializeLightAhead(chunk, false);
            initialized[index] = true;
            IntStream.range(0, chunks.size()).filter(candidate -> !lit[candidate] && neighborsInitialized(chunks.get(candidate).getPos(), initialized)).forEach(candidate -> {
                ProtoChunk ready = chunks.get(candidate);
                engine.lightChunk(ready, false);
                ready.setPersistedStatus(ChunkStatus.LIGHT);
                ready.setLightEngine(engine);
                lit[candidate] = true;
            });
        }

        settle(engine);
    }

    @Override
    public void settle(FireflyLightEngine engine) {
        IntStream.range(0, TestWorld.SIZE * TestWorld.SIZE)
            .mapToObj(chunk -> engine.waitForPendingTasks(chunk / TestWorld.SIZE, chunk % TestWorld.SIZE))
            .toList()
            .forEach(CompletableFuture::join);
    }

    private static boolean neighborsInitialized(ChunkPos pos, boolean[] initialized) {
        return ChunkPos.rangeClosed(pos, 1)
            .filter(neighbor -> neighbor.x() >= 0 && neighbor.z() >= 0 && neighbor.x() < TestWorld.SIZE && neighbor.z() < TestWorld.SIZE)
            .allMatch(neighbor -> initialized[neighbor.x() * TestWorld.SIZE + neighbor.z()]);
    }
}
