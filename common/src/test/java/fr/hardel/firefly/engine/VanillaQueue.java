package fr.hardel.firefly.engine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntSupplier;
import net.minecraft.server.level.ChunkTaskDispatcher;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.thread.ConsecutiveExecutor;

final class VanillaQueue {
    private final Method addTask;
    private final Object preUpdate;
    private final Object postUpdate;

    VanillaQueue() throws ReflectiveOperationException {
        Class<?> taskType = Class.forName("net.minecraft.server.level.ThreadedLevelLightEngine$TaskType");
        this.addTask = ThreadedLevelLightEngine.class.getDeclaredMethod("addTask", int.class, int.class, IntSupplier.class, taskType, Runnable.class);
        this.addTask.setAccessible(true);
        this.preUpdate = constant(taskType, "PRE_UPDATE");
        this.postUpdate = constant(taskType, "POST_UPDATE");
    }

    static ConsecutiveExecutor executor() {
        return new ConsecutiveExecutor(Runnable::run, "light");
    }

    static ChunkTaskDispatcher dispatcher(ConsecutiveExecutor executor) {
        return new ChunkTaskDispatcher(executor, Runnable::run);
    }

    void addPreUpdate(ThreadedLevelLightEngine engine, int chunkX, int chunkZ, Runnable task) {
        add(engine, chunkX, chunkZ, this.preUpdate, task);
    }

    void addPostUpdate(ThreadedLevelLightEngine engine, int chunkX, int chunkZ, Runnable task) {
        add(engine, chunkX, chunkZ, this.postUpdate, task);
    }

    CompletableFuture<Void> waitForPostUpdate(ThreadedLevelLightEngine engine, int chunkX, int chunkZ) {
        return CompletableFuture.runAsync(() -> {}, task -> addPostUpdate(engine, chunkX, chunkZ, task));
    }

    private void add(ThreadedLevelLightEngine engine, int chunkX, int chunkZ, Object type, Runnable task) {
        try {
            this.addTask.invoke(engine, chunkX, chunkZ, (IntSupplier) () -> 0, type, task);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static Object constant(Class<?> taskType, String name) throws ReflectiveOperationException {
        Field field = taskType.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
}
