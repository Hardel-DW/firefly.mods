package fr.hardel.firefly.engine;

final class LightQueue {
    private static final int DATA_BITS = 12;
    private static final int INITIAL_CAPACITY = 1 << 12;

    private long[] entries = new long[INITIAL_CAPACITY];
    private int head;
    private int tail;

    void add(int cell, long data) {
        if (this.tail - this.head == this.entries.length) {
            grow();
        }

        this.entries[this.tail++ & this.entries.length - 1] = (long) cell << DATA_BITS | data;
    }

    long next() {
        return this.entries[this.head++ & this.entries.length - 1];
    }

    boolean isEmpty() {
        return this.head == this.tail;
    }

    static int cell(long entry) {
        return (int) (entry >>> DATA_BITS);
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
}
