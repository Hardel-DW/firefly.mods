package fr.hardel.firefly.storage;

import java.util.Arrays;
import net.minecraft.world.level.chunk.DataLayer;

public final class Nibbles {
    public static final int BYTES = 2048;
    public static final byte[] ZERO = new byte[BYTES];
    public static final byte[] FULL = filled();
    private static final int LAYER_BYTES = 128;

    private Nibbles() {
    }

    public static int index(int x, int y, int z) {
        return y << 8 | z << 4 | x;
    }

    public static int get(byte[] data, int index) {
        return data[index >> 1] >> ((index & 1) << 2) & 15;
    }

    public static void set(byte[] data, int index, int level) {
        int shift = (index & 1) << 2;
        int position = index >> 1;
        data[position] = (byte) (data[position] & ~(15 << shift) | (level & 15) << shift);
    }

    public static byte[] of(DataLayer data) {
        if (data.isEmpty())
            return ZERO;

        return data.isDefinitelyHomogenous() && data.get(0, 0, 0) == 15 ? FULL : canonical(data.getData());
    }

    public static byte[] canonical(byte[] data) {
        if (Arrays.equals(data, ZERO))
            return ZERO;

        return Arrays.equals(data, FULL) ? FULL : data;
    }

    public static boolean shared(byte[] data) {
        return data == ZERO || data == FULL;
    }

    public static byte[] bottomLayerRepeated(byte[] data) {
        if (shared(data))
            return data;

        byte[] repeated = new byte[BYTES];
        for (int layer = 0; layer < 16; layer++) {
            System.arraycopy(data, 0, repeated, layer * LAYER_BYTES, LAYER_BYTES);
        }

        return repeated;
    }

    private static byte[] filled() {
        byte[] data = new byte[BYTES];
        Arrays.fill(data, (byte) -1);
        return data;
    }
}
