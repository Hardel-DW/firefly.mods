package fr.hardel.firefly.storage;

import net.minecraft.world.level.LightLayer;
import org.jspecify.annotations.Nullable;

public final class LightColumn {
    private final LightSections block;
    private final LightSections sky;
    private volatile @Nullable Boolean initialization;

    public LightColumn(int sections) {
        this.block = new LightSections(sections);
        this.sky = new LightSections(sections);
    }

    public LightSections sections(LightLayer layer) {
        return layer == LightLayer.BLOCK ? this.block : this.sky;
    }

    public void requestInitialization(boolean lighted) {
        this.initialization = lighted;
    }

    public @Nullable Boolean takeInitialization() {
        Boolean lighted = this.initialization;
        this.initialization = null;
        return lighted;
    }
}
