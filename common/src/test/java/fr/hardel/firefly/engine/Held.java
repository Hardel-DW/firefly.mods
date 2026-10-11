package fr.hardel.firefly.engine;

import fr.hardel.firefly.schedule.LightExecutor;
import fr.hardel.firefly.server.FireflyLightEngine;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;
import net.minecraft.world.level.chunk.LightChunkGetter;

final class Held implements LightExecutor, LightOracle.Engine<FireflyLightEngine> {
    private final Deque<Runnable> tasks = new ArrayDeque<>();

    @Override
    public void execute(LightChunkGetter level, int chunkX, int chunkZ, Runnable task) {
        this.tasks.add(task);
    }

    @Override
    public FireflyLightEngine create(LightChunkGetter world, boolean sky) {
        return new FireflyLightEngine(world, null, sky, null, null, LightEmission.VANILLA, this);
    }

    @Override
    public void settle(FireflyLightEngine engine) {
        engine.tryScheduleUpdate();
        runUntil(this.tasks::isEmpty);
    }

    int waiting() {
        return this.tasks.size();
    }

    void runUntil(BooleanSupplier done) {
        while (!done.getAsBoolean()) {
            this.tasks.removeLast().run();
        }
    }
}
