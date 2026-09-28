package fr.hardel.firefly.storage;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import org.jspecify.annotations.Nullable;

public final class QueuedSections {
    private final ConcurrentHashMap<Long, Column> columns = new ConcurrentHashMap<>();
    private final int sections;

    public QueuedSections(int sections) {
        this.sections = sections;
    }

    public void queue(LightLayer layer, int chunkX, int chunkZ, int section, byte @Nullable [] data) {
        Column column = data == null ? this.columns.get(ChunkPos.pack(chunkX, chunkZ)) : this.columns.computeIfAbsent(ChunkPos.pack(chunkX, chunkZ), _ -> new Column(this.sections));
        if (column != null) {
            column.put(layer, section, data);
        }
    }

    public void retain(int chunkX, int chunkZ, boolean retain) {
        Column column = retain ? this.columns.computeIfAbsent(ChunkPos.pack(chunkX, chunkZ), _ -> new Column(this.sections)) : this.columns.get(ChunkPos.pack(chunkX, chunkZ));
        if (column != null) {
            column.retain = retain;
        }
    }

    public void clear(int chunkX, int chunkZ) {
        this.columns.remove(ChunkPos.pack(chunkX, chunkZ));
    }

    public @Nullable Column column(int chunkX, int chunkZ) {
        return this.columns.get(ChunkPos.pack(chunkX, chunkZ));
    }

    public byte @Nullable [] get(LightLayer layer, int chunkX, int chunkZ, int section) {
        Column column = column(chunkX, chunkZ);
        return column == null ? null : column.get(layer, section);
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
