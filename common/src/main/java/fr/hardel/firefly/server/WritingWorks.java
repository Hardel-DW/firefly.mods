package fr.hardel.firefly.server;

import fr.hardel.firefly.engine.ChunkWork;
import fr.hardel.firefly.schedule.LightExecutor;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.world.level.ChunkPos;

final class WritingWorks {
    private static final int STRIPES = 64;

    private final Stripe[] stripes = Stream.generate(Stripe::new).limit(STRIPES).toArray(Stripe[]::new);

    void add(ChunkWork work) {
        for (int dx = -LightExecutor.RADIUS; dx <= LightExecutor.RADIUS; dx++) {
            for (int dz = -LightExecutor.RADIUS; dz <= LightExecutor.RADIUS; dz++) {
                long chunk = ChunkPos.pack(work.chunkX() + dx, work.chunkZ() + dz);
                stripe(chunk).add(chunk, work);
            }
        }
    }

    void remove(ChunkWork work) {
        for (int dx = -LightExecutor.RADIUS; dx <= LightExecutor.RADIUS; dx++) {
            for (int dz = -LightExecutor.RADIUS; dz <= LightExecutor.RADIUS; dz++) {
                long chunk = ChunkPos.pack(work.chunkX() + dx, work.chunkZ() + dz);
                stripe(chunk).remove(chunk, work);
            }
        }
    }

    CompletableFuture<Void> finished(int chunkX, int chunkZ) {
        long chunk = ChunkPos.pack(chunkX, chunkZ);
        return CompletableFuture.allOf(stripe(chunk).finished(chunk).toArray(CompletableFuture[]::new));
    }

    private Stripe stripe(long chunk) {
        return this.stripes[(int) HashCommon.mix(chunk) & STRIPES - 1];
    }

    private static final class Stripe {
        private final Long2ObjectOpenHashMap<ObjectArrayList<ChunkWork>> works = new Long2ObjectOpenHashMap<>();

        private synchronized void add(long chunk, ChunkWork work) {
            this.works.computeIfAbsent(chunk, _ -> new ObjectArrayList<>()).add(work);
        }

        private synchronized void remove(long chunk, ChunkWork work) {
            ObjectArrayList<ChunkWork> writing = this.works.get(chunk);
            writing.remove(work);
            if (writing.isEmpty()) {
                this.works.remove(chunk);
            }
        }

        private synchronized List<CompletableFuture<Void>> finished(long chunk) {
            ObjectArrayList<ChunkWork> writing = this.works.get(chunk);
            return writing == null ? List.of() : writing.stream().map(ChunkWork::finished).toList();
        }
    }
}
