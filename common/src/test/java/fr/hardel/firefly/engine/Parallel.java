package fr.hardel.firefly.engine;

import fr.hardel.firefly.schedule.ReservingExecutor;
import fr.hardel.firefly.server.FireflyLightEngine;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import net.minecraft.world.level.chunk.LightChunkGetter;

record Parallel(LightEmission emission) implements LightOracle.Engine<FireflyLightEngine> {
    static final ReservingExecutor EXECUTOR = new ReservingExecutor(4);

    @Override
    public FireflyLightEngine create(LightChunkGetter world, boolean sky) {
        return new FireflyLightEngine(world, null, sky, null, null, this.emission, EXECUTOR);
    }

    @Override
    public void settle(FireflyLightEngine engine) {
        IntStream.range(0, TestWorld.SIZE * TestWorld.SIZE)
            .mapToObj(chunk -> engine.waitForPendingTasks(chunk / TestWorld.SIZE, chunk % TestWorld.SIZE))
            .toList()
            .forEach(CompletableFuture::join);
    }
}
