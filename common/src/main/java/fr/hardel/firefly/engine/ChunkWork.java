package fr.hardel.firefly.engine;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

public final class ChunkWork {
    private static final int ENABLE = 0;
    private static final int DISABLE = 1;
    private static final int PROPAGATE = 2;
    private static final int WRITE_RADIUS = LightWindow.RADIUS - 1;

    private final int chunkX;
    private final int chunkZ;
    private final IntArrayList operations = new IntArrayList();
    private final LongOpenHashSet checks = new LongOpenHashSet();
    private final List<Runnable> completions = new ArrayList<>();
    private final List<CompletableFuture<?>> futures = new ArrayList<>();
    private LightBatch batch;
    private boolean propagating;
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

    public LightBatch batch() {
        return this.batch;
    }

    public void batch(LightBatch batch) {
        this.batch = batch;
    }

    public int radius() {
        return this.checks.isEmpty() && !this.propagating ? WRITE_RADIUS : LightWindow.RADIUS;
    }

    public void check(BlockPos pos) {
        this.checks.add(pos.asLong());
    }

    public void lightEnabled(boolean enabled) {
        this.operations.add(enabled ? ENABLE : DISABLE);
    }

    public void propagateLightSources() {
        this.propagating = true;
        this.operations.add(PROPAGATE);
    }

    public void unload() {
        this.unloading = true;
    }

    public void onComplete(Runnable completion) {
        this.completions.add(completion);
    }

    public <T> void completes(CompletableFuture<T> future, T value) {
        this.futures.add(future);
        this.completions.add(() -> future.complete(value));
    }


    void run(LightWindow window, List<Propagator> propagators) {
        window.refreshStorage(this.unloading);
        window.initializePending(propagators);
        if (!window.hasCenter()) {
            propagators.forEach(Propagator::run);
            return;
        }

        apply(propagators);
        LongIterator positions = this.checks.iterator();
        while (positions.hasNext()) {
            long pos = positions.nextLong();
            int y = BlockPos.getY(pos);
            if (window.insideHeight(y)) {
                int cell = window.cell(BlockPos.getX(pos), y, BlockPos.getZ(pos));
                propagators.forEach(propagator -> propagator.check(cell));
            }
        }

        if (this.unloading) {
            window.unload();
        }

        propagators.forEach(Propagator::run);
    }

    public void complete(@Nullable Throwable failure) {
        if (failure != null) {
            this.futures.forEach(future -> future.completeExceptionally(failure));
            return;
        }

        this.completions.forEach(Runnable::run);
    }

    private void apply(List<Propagator> propagators) {
        for (int index = 0; index < this.operations.size(); index++) {
            switch (this.operations.getInt(index)) {
                case ENABLE -> propagators.forEach(propagator -> propagator.setLightEnabled(LightWindow.CENTER, true));
                case DISABLE -> propagators.forEach(propagator -> propagator.setLightEnabled(LightWindow.CENTER, false));
                default -> propagators.forEach(Propagator::propagateLightSources);
            }
        }
    }
}
