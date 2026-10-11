package fr.hardel.firefly.storage;

import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.level.LightLayer;
import org.jspecify.annotations.Nullable;

public final class LightColumn {
    private final LightSections block;
    private final LightSections sky;
    private final AtomicReference<@Nullable Boolean> initialization = new AtomicReference<>();
    private volatile @Nullable PendingLight queued;
    private volatile @Nullable PendingLight saved;
    private volatile boolean reconciling;

    public LightColumn(int sections) {
        this.block = new LightSections(sections);
        this.sky = new LightSections(sections);
    }

    public LightSections sections(LightLayer layer) {
        return layer == LightLayer.BLOCK ? this.block : this.sky;
    }

    public void requestInitialization(boolean lighted) {
        this.initialization.set(lighted);
    }

    public @Nullable Boolean initialization() {
        return this.initialization.get();
    }

    public void initialized(Boolean request) {
        this.initialization.compareAndSet(request, null);
    }

    public @Nullable PendingLight queued() {
        return this.queued;
    }

    public synchronized PendingLight queuedOrCreate() {
        PendingLight light = this.queued;
        if (light != null)
            return light;

        PendingLight created = new PendingLight(this.block.count());
        this.queued = created;
        return created;
    }

    public @Nullable PendingLight saved() {
        return this.saved;
    }

    public void save(PendingLight light) {
        this.saved = light;
    }

    public void forgetSaved() {
        this.saved = null;
    }

    public void requestReconciliation() {
        this.reconciling = true;
    }

    public boolean takeReconciliation() {
        boolean requested = this.reconciling;
        this.reconciling = false;
        return requested;
    }
}
