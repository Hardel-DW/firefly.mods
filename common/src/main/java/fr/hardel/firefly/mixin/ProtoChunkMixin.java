package fr.hardel.firefly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ProtoChunk.class)
public abstract class ProtoChunkMixin {
    @WrapOperation(method = "setBlockState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/lighting/ChunkSkyLightSources;update(Lnet/minecraft/world/level/BlockGetter;III)Z"))
    private boolean firefly$updateSourcesUnderTheirLock(ChunkSkyLightSources sources, BlockGetter level, int x, int y, int z, Operation<Boolean> original) {
        synchronized (sources) {
            return original.call(sources, level, x, y, z);
        }
    }
}
