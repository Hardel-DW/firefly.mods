package fr.hardel.firefly.engine;

import java.util.List;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;

public final class LevelLight {
    private final LightChunkGetter source;
    private final LightEmission emission;
    private final ChunkSkyLightSources emptySources;
    private final boolean hasSky;
    private final int minSection;
    private final int sections;

    public LevelLight(LightChunkGetter source, LightEmission emission, boolean hasSky, int minSection, int sections) {
        this.source = source;
        this.emission = emission;
        this.emptySources = new ChunkSkyLightSources(source.getLevel());
        this.hasSky = hasSky;
        this.minSection = minSection;
        this.sections = sections;
    }

    public void run(int chunkX, int chunkZ, ChunkWork work) {
        LightWindow window = new LightWindow(this.source, this.emission, this.emptySources, this.hasSky, chunkX, chunkZ, this.minSection, this.sections);
        List<Propagator> propagators = window.sky == null ? List.of(new BlockPropagator(window)) : List.of(new BlockPropagator(window), new SkyPropagator(window, window.sky));
        work.apply(window, propagators);
        propagators.forEach(Propagator::run);
        window.publish();
    }
}
