package fr.hardel.firefly.storage;

import org.jspecify.annotations.Nullable;

public interface ChunkLight {
    LightColumn firefly$light();

    @Nullable LightColumn firefly$lightIfPresent();
}
