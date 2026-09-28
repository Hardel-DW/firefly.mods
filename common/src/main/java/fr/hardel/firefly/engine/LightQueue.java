package fr.hardel.firefly.engine;

import net.minecraft.core.Direction;

final class LightQueue {
    static final int ALL_DIRECTIONS = 63;
    private static final int CELL_BITS = 27;
    private static final long CELL_MASK = (1L << CELL_BITS) - 1;
    private static final int LEVEL_SHIFT = CELL_BITS;
    private static final int DIRECTIONS_SHIFT = LEVEL_SHIFT + 4;
    private static final long FROM_EMPTY_SHAPE = 1L << DIRECTIONS_SHIFT + 6;
    private static final long FROM_EMISSION = FROM_EMPTY_SHAPE << 1;
    private static final int INITIAL_CAPACITY = 1 << 12;

    private long[] entries = new long[INITIAL_CAPACITY];
    private int head;
    private int tail;

    void add(long entry) {
        if (this.tail - this.head == this.entries.length) {
            grow();
        }

        this.entries[this.tail++ & this.entries.length - 1] = entry;
    }

    long next() {
        return this.entries[this.head++ & this.entries.length - 1];
    }

    boolean isEmpty() {
        return this.head == this.tail;
    }

    private void grow() {
        long[] grown = new long[this.entries.length << 1];
        int size = this.tail - this.head;
        for (int index = 0; index < size; index++) {
            grown[index] = this.entries[this.head + index & this.entries.length - 1];
        }

        this.entries = grown;
        this.head = 0;
        this.tail = size;
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
