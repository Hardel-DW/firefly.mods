package fr.hardel.firefly.engine;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

public final class ChunkWork {
    private static final int SECTION_FILLED = 0;
    private static final int SECTION_EMPTY = 1;
    private static final int ENABLE = 2;
    private static final int DISABLE = 3;
    private static final int PROPAGATE = 4;
    private static final int KIND_BITS = 3;

    private final int chunkX;
    private final int chunkZ;
    private final IntArrayList operations = new IntArrayList();
    private final IntArrayList lastOperations = new IntArrayList();
    private final LongOpenHashSet checks = new LongOpenHashSet();
    private final List<Runnable> completions = new ArrayList<>();
    private boolean unloading;

    public ChunkWork(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public int chunkX() {
        return this.chunkX;
    }

    public int chunkZ() {
        return this.chunkZ;
    }

    public void check(BlockPos pos) {
        this.checks.add(pos.asLong());
    }

    public void sectionStatus(int section, boolean empty) {
        this.operations.add(section << KIND_BITS | (empty ? SECTION_EMPTY : SECTION_FILLED));
    }

    public void lightEnabled(boolean enabled) {
        this.operations.add(enabled ? ENABLE : DISABLE);
    }

    public void lightEnabledAfterUpdate(boolean enabled) {
        this.lastOperations.add(enabled ? ENABLE : DISABLE);
    }

    public void propagateLightSources() {
        this.operations.add(PROPAGATE);
    }

    public void unload() {
        this.unloading = true;
    }

    public void onComplete(Runnable completion) {
        this.completions.add(completion);
    }

    void run(LightWindow window, List<Propagator> propagators) {
        if (!window.hasCenter()) {
            unloadIfAsked(window);
            propagators.forEach(Propagator::run);
            return;
        }

        apply(this.operations, window, propagators);
        LongIterator positions = this.checks.iterator();
        while (positions.hasNext()) {
            long pos = positions.nextLong();
            int y = BlockPos.getY(pos);
            if (window.insideHeight(y)) {
                int cell = window.cell(BlockPos.getX(pos), y, BlockPos.getZ(pos));
                propagators.forEach(propagator -> propagator.check(cell));
            }
        }

        unloadIfAsked(window);
        propagators.forEach(Propagator::run);
        apply(this.lastOperations, window, propagators);
    }

    void complete() {
        this.completions.forEach(Runnable::run);
    }

    private void unloadIfAsked(LightWindow window) {
        if (this.unloading) {
            window.unload();
        }
    }

    private static void apply(IntArrayList operations, LightWindow window, List<Propagator> propagators) {
        for (int index = 0; index < operations.size(); index++) {
            int operation = operations.getInt(index);
            switch (operation & (1 << KIND_BITS) - 1) {
                case SECTION_FILLED -> window.sectionStatus(operation >> KIND_BITS, false);
                case SECTION_EMPTY -> window.sectionStatus(operation >> KIND_BITS, true);
                case ENABLE -> propagators.forEach(propagator -> propagator.setLightEnabled(true));
                case DISABLE -> propagators.forEach(propagator -> propagator.setLightEnabled(false));
                default -> propagators.forEach(Propagator::propagateLightSources);
            }
        }
    }
}
