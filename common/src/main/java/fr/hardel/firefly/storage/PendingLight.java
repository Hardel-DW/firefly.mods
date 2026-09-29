package fr.hardel.firefly.storage;

import net.minecraft.world.level.LightLayer;
import org.jspecify.annotations.Nullable;

public final class PendingLight {
    private final byte[][] block;
    private final byte[][] sky;
    private volatile boolean retained;

    public PendingLight(int sections) {
        this.block = new byte[sections][];
        this.sky = new byte[sections][];
    }

    public synchronized byte @Nullable [] get(LightLayer layer, int section) {
        return data(layer)[section];
    }

    public synchronized void put(LightLayer layer, int section, byte @Nullable [] data) {
        data(layer)[section] = data;
    }

    public synchronized void remove(LightLayer layer, int section, byte[] data) {
        if (data(layer)[section] == data) {
            data(layer)[section] = null;
        }
    }

    public boolean retained() {
        return this.retained;
    }

    public void retain(boolean retain) {
        this.retained = retain;
    }

    private byte[][] data(LightLayer layer) {
        return layer == LightLayer.BLOCK ? this.block : this.sky;
    }
}
