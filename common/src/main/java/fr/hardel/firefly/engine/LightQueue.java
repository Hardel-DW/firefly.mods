package fr.hardel.firefly.engine;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.minecraft.core.Direction;

final class LightQueue {
    static final int ALL_DIRECTIONS = 63;
    private static final int CELL_BITS = 27;
    private static final long CELL_MASK = (1L << CELL_BITS) - 1;
    private static final int LEVEL_SHIFT = CELL_BITS;
    private static final int DIRECTIONS_SHIFT = LEVEL_SHIFT + 4;
    private static final long FROM_EMPTY_SHAPE = 1L << DIRECTIONS_SHIFT + 6;
    private static final long FROM_EMISSION = FROM_EMPTY_SHAPE << 1;

    private final LongArrayFIFOQueue entries = new LongArrayFIFOQueue();

    void add(long entry) {
        this.entries.enqueue(entry);
    }

    long next() {
        return this.entries.dequeueLong();
    }

    boolean isEmpty() {
        return this.entries.isEmpty();
    }

    static long entry(int cell, int level, int directions, boolean fromEmptyShape, boolean fromEmission) {
        long entry = cell | (long) level << LEVEL_SHIFT | (long) directions << DIRECTIONS_SHIFT;
        entry |= fromEmptyShape ? FROM_EMPTY_SHAPE : 0;
        return entry | (fromEmission ? FROM_EMISSION : 0);
    }

    static int allBut(Direction direction) {
        return ALL_DIRECTIONS & ~only(direction);
    }

    static int only(Direction direction) {
        return 1 << direction.ordinal();
    }

    static int cell(long entry) {
        return (int) (entry & CELL_MASK);
    }

    static int level(long entry) {
        return (int) (entry >>> LEVEL_SHIFT & 15);
    }

    static boolean goes(long entry, Direction direction) {
        return (entry >>> DIRECTIONS_SHIFT & only(direction)) != 0;
    }

    static boolean fromEmptyShape(long entry) {
        return (entry & FROM_EMPTY_SHAPE) != 0;
    }

    static boolean fromEmission(long entry) {
        return (entry & FROM_EMISSION) != 0;
    }
}
