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
    private final ExecutorService pool;
    private final Map<LightChunkGetter, Long2ObjectOpenHashMap<Reservation>> lastByChunk = new IdentityHashMap<>();

    public ReservingExecutor(int threads) {
        this.pool = Executors.newFixedThreadPool(threads, new DaemonThreads());
    }

    public static ReservingExecutor shared() {
        return Shared.INSTANCE;
    }

    @Override
    public void execute(LightChunkGetter level, int chunkX, int chunkZ, int radius, Runnable task) {
        Reservation reservation = new Reservation(level, chunkX, chunkZ, radius, task);
        boolean ready;
        synchronized (this.lastByChunk) {
            ready = queue(reservation);
        }

        if (ready) {
            this.pool.execute(reservation);
        }
    }

    private boolean queue(Reservation reservation) {
        Long2ObjectOpenHashMap<Reservation> last = this.lastByChunk.computeIfAbsent(reservation.level, _ -> new Long2ObjectOpenHashMap<>());
        for (long chunk : reservation.area) {
            Reservation previous = last.put(chunk, reservation);
            if (previous != null) {
                previous.next[previous.indexOf(chunk)] = reservation;
                reservation.before++;
            }
        }

        return reservation.before == 0;
    }

    private List<Reservation> release(Reservation reservation) {
        List<Reservation> ready = new ArrayList<>();
        synchronized (this.lastByChunk) {
            Long2ObjectOpenHashMap<Reservation> last = this.lastByChunk.get(reservation.level);
            for (int index = 0; index < reservation.area.length; index++) {
                Reservation next = reservation.next[index];
                if (next == null) {
                    last.remove(reservation.area[index]);
                }

                if (next != null && --next.before == 0) {
                    ready.add(next);
                }
            }

            if (last.isEmpty()) {
                this.lastByChunk.remove(reservation.level);
            }
        }

        return ready;
    }

    private final class Reservation implements Runnable {
        private final LightChunkGetter level;
        private final int minX;
        private final int minZ;
        private final int width;
        private final long[] area;
        private final Reservation[] next;
        private final Runnable task;
        private int before;

        private Reservation(LightChunkGetter level, int chunkX, int chunkZ, int radius, Runnable task) {
            this.level = level;
            this.task = task;
            this.minX = chunkX - radius;
            this.minZ = chunkZ - radius;
            this.width = 2 * radius + 1;
            this.area = new long[this.width * this.width];
            this.next = new Reservation[this.area.length];
            for (int index = 0; index < this.area.length; index++) {
                this.area[index] = ChunkPos.pack(this.minX + index / this.width, this.minZ + index % this.width);
            }
        }

        private int indexOf(long chunk) {
            return (ChunkPos.getX(chunk) - this.minX) * this.width + ChunkPos.getZ(chunk) - this.minZ;
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
