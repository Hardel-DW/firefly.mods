package fr.hardel.firefly.mixin;

import fr.hardel.firefly.storage.ChunkLight;
import fr.hardel.firefly.storage.LightColumn;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChunkAccess.class)
public abstract class ChunkAccessMixin implements ChunkLight {
    @Shadow
    @Final
    protected LevelHeightAccessor levelHeightAccessor;

    @Unique
    private volatile @Nullable LightColumn firefly$light;

    @Override
    public LightColumn firefly$light() {
        LightColumn light = this.firefly$light;
        if (light != null)
            return light;

        LightColumn created = new LightColumn(this.levelHeightAccessor.getSectionsCount() + 2);
        this.firefly$light = created;
        return created;
    }

    @Override
    public @Nullable LightColumn firefly$lightIfPresent() {
        return this.firefly$light;
    }
}
