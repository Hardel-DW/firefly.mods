package fr.hardel.firefly.storage;

import net.minecraft.world.level.LightLayer;

public final class LightColumn {
    private final LightSections block;
    private final LightSections sky;
    private final long[] nonEmpty;

    public LightColumn(int sections) {
        this.block = new LightSections(sections);
        this.sky = new LightSections(sections);
        this.nonEmpty = new long[(sections + 63) >> 6];
    }

    public LightSections sections(LightLayer layer) {
        return layer == LightLayer.BLOCK ? this.block : this.sky;
    }

    public boolean nonEmpty(int index) {
        return (this.nonEmpty[index >> 6] & 1L << index) != 0;
    }

    public void setNonEmpty(int index, boolean nonEmpty) {
        if (nonEmpty) {
            this.nonEmpty[index >> 6] |= 1L << index;
            return;
        }

        this.nonEmpty[index >> 6] &= ~(1L << index);
    }
}
