package fr.hardel.firefly.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ReservingExecutorTest {
    private static final int COLLECTIONS = 10;
    private static final int RADIUS = 2;

    @Test
    void overlappingTasksRunInTheOrderTheyWereSubmitted() {
        ReservingExecutor executor = new ReservingExecutor(4);
        EmptyLevel level = new EmptyLevel();
        List<String> order = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> release = new CompletableFuture<>();
        CompletableFuture<Void> done = new CompletableFuture<>();

        executor.execute(level, 0, 0, RADIUS, () -> {
            release.join();
            order.add("first");
        });
        executor.execute(level, 4, 0, RADIUS, () -> order.add("second"));
        executor.execute(level, 8, 0, RADIUS, () -> {
            order.add("third");
            done.complete(null);
        });
        release.complete(null);
        done.join();

        assertEquals(List.of("first", "second", "third"), order);
    }

    @Test
    void aLevelWithNoReservedChunkIsNotRetained() {
        ReservingExecutor executor = new ReservingExecutor(1);
        WeakReference<LightChunkGetter> level = lightOnce(executor);
        lightOnce(executor);

        for (int collection = 0; collection < COLLECTIONS && level.get() != null; collection++) {
            System.gc();
        }

        assertNull(level.get());
    }

    private static WeakReference<LightChunkGetter> lightOnce(ReservingExecutor executor) {
        EmptyLevel level = new EmptyLevel();
        CompletableFuture<Void> done = new CompletableFuture<>();
        executor.execute(level, 0, 0, RADIUS, () -> done.complete(null));
        done.join();
        return new WeakReference<>(level);
    }

    private static final class EmptyLevel implements LightChunkGetter {
        @Override
        public @Nullable LightChunk getChunkForLighting(int x, int z) {
            return null;
        }

        @Override
        public BlockGetter getLevel() {
            return EmptyBlockGetter.INSTANCE;
        }
    }
}
