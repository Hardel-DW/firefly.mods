package fr.hardel.firefly.mixin;

import fr.hardel.firefly.storage.ChunkLight;
import fr.hardel.firefly.storage.LightColumn;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkAccess.class)
public abstract class ChunkAccessMixin implements ChunkLight {
    @Shadow
    @Final
    protected LevelHeightAccessor levelHeightAccessor;

    @Unique
    private LightColumn firefly$light;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void firefly$createLight(CallbackInfo callback) {
        this.firefly$light = new LightColumn(this.levelHeightAccessor.getSectionsCount() + 2);
    }

    @Override
    public LightColumn firefly$light() {
        return this.firefly$light;
    }

    @Override
    public void firefly$setLight(LightColumn light) {
        this.firefly$light = light;
    }
}
