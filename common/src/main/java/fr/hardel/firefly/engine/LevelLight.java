package fr.hardel.firefly.engine;

import fr.hardel.firefly.storage.QueuedSections;
import java.util.ArrayDeque;
import java.util.Deque;
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
    private final Deque<Workspace> idle = new ArrayDeque<>();

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
        Workspace workspace = take();
        workspace.window.open(work.chunkX(), work.chunkZ());
        work.run(workspace.window, workspace.propagators);
        workspace.window.publish();
        workspace.window.close();
        synchronized (this.idle) {
            this.idle.push(workspace);
        }

        work.complete();
    }

    private Workspace take() {
        synchronized (this.idle) {
            if (!this.idle.isEmpty())
                return this.idle.pop();
        }

        LightWindow window = new LightWindow(this.source, this.emission, this.queued, this.emptySources, this.hasSky, this.minSection, this.sections);
        List<Propagator> propagators = window.sky == null ? List.of(new BlockPropagator(window)) : List.of(new BlockPropagator(window), new SkyPropagator(window, window.sky));
        return new Workspace(window, propagators);
    }

    private record Workspace(LightWindow window, List<Propagator> propagators) {
    }
}
