package fr.hardel.firefly.engine;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

public final class LightBatch {
    private final LongSet block = new LongOpenHashSet();
    private final LongSet sky = new LongOpenHashSet();
    private final AtomicInteger remaining;

    public LightBatch(int works) {
        this.remaining = new AtomicInteger(works);
    }

    synchronized void changed(LightLayer layer, long section) {
        (layer == LightLayer.BLOCK ? this.block : this.sky).add(section);
    }

    public void finish(LightChunkGetter source) {
        if (this.remaining.decrementAndGet() == 0) {
            send(source, LightLayer.BLOCK, this.block);
            send(source, LightLayer.SKY, this.sky);
        }
    }

    private static void send(LightChunkGetter source, LightLayer layer, LongSet sections) {
        LongIterator iterator = sections.iterator();
        while (iterator.hasNext()) {
            source.onLightUpdate(layer, SectionPos.of(iterator.nextLong()));
        }
    }
}
