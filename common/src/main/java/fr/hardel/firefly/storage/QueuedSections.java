package fr.hardel.firefly.storage;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.stream.Stream;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import org.jspecify.annotations.Nullable;

public final class QueuedSections {
    private static final int STRIPES = 64;

    private final Stripe[] stripes = Stream.generate(Stripe::new).limit(STRIPES).toArray(Stripe[]::new);
    private final int sections;

    public QueuedSections(int sections) {
        this.sections = sections;
    }

    public void queue(LightLayer layer, int chunkX, int chunkZ, int section, byte @Nullable [] data) {
        long chunk = ChunkPos.pack(chunkX, chunkZ);
        Column column = data == null ? column(chunkX, chunkZ) : stripe(chunk).columnOrCreate(chunk, this.sections);
        if (column != null) {
            column.put(layer, section, data);
        }
    }

    public void retain(int chunkX, int chunkZ, boolean retain) {
        long chunk = ChunkPos.pack(chunkX, chunkZ);
        Column column = retain ? stripe(chunk).columnOrCreate(chunk, this.sections) : column(chunkX, chunkZ);
        if (column != null) {
            column.retain = retain;
        }
    }

    public void clear(int chunkX, int chunkZ) {
        long chunk = ChunkPos.pack(chunkX, chunkZ);
        stripe(chunk).remove(chunk);
    }

    public @Nullable Column column(int chunkX, int chunkZ) {
        long chunk = ChunkPos.pack(chunkX, chunkZ);
        return stripe(chunk).column(chunk);
    }

    public byte @Nullable [] get(LightLayer layer, int chunkX, int chunkZ, int section) {
        Column column = column(chunkX, chunkZ);
        return column == null ? null : column.get(layer, section);
    }

    private Stripe stripe(long chunk) {
        return this.stripes[(int) HashCommon.mix(chunk) & STRIPES - 1];
    }

    private static final class Stripe {
        private final Long2ObjectOpenHashMap<Column> columns = new Long2ObjectOpenHashMap<>();

        private synchronized @Nullable Column column(long chunk) {
            return this.columns.get(chunk);
        }

        private synchronized Column columnOrCreate(long chunk, int sections) {
            return this.columns.computeIfAbsent(chunk, _ -> new Column(sections));
        }

        private synchronized void remove(long chunk) {
            this.columns.remove(chunk);
        }
    }

    public static final class Column {
        private final byte[][] block;
        private final byte[][] sky;
        private volatile boolean retain;

        private Column(int sections) {
            this.block = new byte[sections][];
            this.sky = new byte[sections][];
        }

        public synchronized byte @Nullable [] get(LightLayer layer, int section) {
            return data(layer)[section];
        }

        public synchronized void remove(LightLayer layer, int section, byte[] data) {
            if (data(layer)[section] == data) {
                data(layer)[section] = null;
            }
        }

        public synchronized void put(LightLayer layer, int section, byte @Nullable [] data) {
            data(layer)[section] = data;
        }

        public boolean retained() {
            return this.retain;
        }

        private byte[][] data(LightLayer layer) {
            return layer == LightLayer.BLOCK ? this.block : this.sky;
        }
    }
}
