package fr.hardel.firefly.engine;

import fr.hardel.firefly.storage.QueuedSections;
import java.util.List;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;

public final class LevelLight {
    private final LightChunkGetter source;
    private final LightEmission emission;
    private final QueuedSections queued;
    private final ChunkSkyLightSources emptySources;
    private final boolean hasSky;
    private final int minSection;
    private final int sections;

    public LevelLight(LightChunkGetter source, LightEmission emission, QueuedSections queued, boolean hasSky, int minSection, int sections) {
        this.source = source;
        this.emission = emission;
        this.queued = queued;
        this.emptySources = new ChunkSkyLightSources(source.getLevel());
        this.hasSky = hasSky;
        this.minSection = minSection;
        this.sections = sections;
    }

    public void run(ChunkWork work) {
        LightWindow window = new LightWindow(this.source, this.emission, this.queued, this.emptySources, this.hasSky, work.chunkX(), work.chunkZ(), this.minSection,
            this.sections);
        List<Propagator> propagators = window.sky == null ? List.of(new BlockPropagator(window)) : List.of(new BlockPropagator(window), new SkyPropagator(window, window.sky));
        work.run(window, propagators);
        window.publish();
        work.complete();
    }
}
