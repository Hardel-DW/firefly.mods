package fr.hardel.firefly.mixin;

import fr.hardel.firefly.storage.ChunkLight;
import fr.hardel.firefly.storage.LightColumn;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ImposterProtoChunk.class)
public abstract class ImposterProtoChunkMixin implements ChunkLight {
    @Shadow
    @Final
    private LevelChunk wrapped;

    @Override
    public LightColumn firefly$light() {
        return ((ChunkLight) this.wrapped).firefly$light();
    }

    @Override
    public void firefly$setLight(LightColumn light) {
        ((ChunkLight) this.wrapped).firefly$setLight(light);
    }
}
