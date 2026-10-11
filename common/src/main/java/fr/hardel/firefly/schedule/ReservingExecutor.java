package fr.hardel.firefly.schedule;

import fr.hardel.firefly.FireflyConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LightChunkGetter;

public final class ReservingExecutor implements LightExecutor {
    private static final int WIDTH = 2 * RADIUS + 1;

    private final ExecutorService pool;
    private final Map<LightChunkGetter, Long2ObjectOpenHashMap<Reservation>> holders = new IdentityHashMap<>();

    public ReservingExecutor(int threads) {
        this.pool = Executors.newFixedThreadPool(threads, new DaemonThreads());
    }

    public static ReservingExecutor shared() {
        return Shared.INSTANCE;
    }

    @Override
    public void execute(LightChunkGetter level, int chunkX, int chunkZ, Runnable task) {
        Reservation reservation = new Reservation(level, chunkX, chunkZ, task);
        boolean held;
        synchronized (this.holders) {
            held = hold(reservation);
        }

        if (held) {
            this.pool.execute(reservation);
        }
    }

    private boolean hold(Reservation reservation) {
        Long2ObjectOpenHashMap<Reservation> held = this.holders.computeIfAbsent(reservation.level, _ -> new Long2ObjectOpenHashMap<>());
        for (long chunk : reservation.area) {
            Reservation holder = held.get(chunk);
            if (holder != null) {
                holder.waiting.add(reservation);
                return false;
            }
        }

        for (long chunk : reservation.area) {
            held.put(chunk, reservation);
        }

        return true;
    }

    private List<Reservation> release(Reservation reservation) {
        List<Reservation> ready = new ArrayList<>();
        synchronized (this.holders) {
            Long2ObjectOpenHashMap<Reservation> held = this.holders.get(reservation.level);
            for (long chunk : reservation.area) {
                held.remove(chunk);
            }

            for (Reservation waiting : reservation.waiting) {
                if (hold(waiting)) {
                    ready.add(waiting);
                }
            }

            if (held.isEmpty()) {
                this.holders.remove(reservation.level);
            }
        }

        return ready;
    }

    private final class Reservation implements Runnable {
        private final LightChunkGetter level;
        private final long[] area = new long[WIDTH * WIDTH];
        private final List<Reservation> waiting = new ArrayList<>(0);
        private final Runnable task;

        private Reservation(LightChunkGetter level, int chunkX, int chunkZ, Runnable task) {
            this.level = level;
            this.task = task;
            for (int index = 0; index < this.area.length; index++) {
                this.area[index] = ChunkPos.pack(chunkX - RADIUS + index / WIDTH, chunkZ - RADIUS + index % WIDTH);
            }
        }

        @Override
        public void run() {
            try {
                this.task.run();
            } finally {
                release(this).forEach(ReservingExecutor.this.pool::execute);
            }
        }
    }

    private static final class DaemonThreads implements ThreadFactory {
        private final AtomicInteger created = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "firefly-%d".formatted(this.created.getAndIncrement()));
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class Shared {
        private static final ReservingExecutor INSTANCE = new ReservingExecutor(FireflyConfig.get().effectiveThreads());
    }
}
