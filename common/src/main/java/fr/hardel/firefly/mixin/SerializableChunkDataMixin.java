package fr.hardel.firefly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.firefly.server.LightReader;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SerializableChunkData.class)
public abstract class SerializableChunkDataMixin {
    @WrapOperation(method = "copyOf", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/lighting/LayerLightEventListener;getDataLayerData(Lnet/minecraft/core/SectionPos;)Lnet/minecraft/world/level/chunk/DataLayer;"))
    private static @Nullable DataLayer firefly$readSavedChunk(LayerLightEventListener listener, SectionPos pos, Operation<DataLayer> original,
        @Local(argsOnly = true) ChunkAccess chunk) {
        if (listener instanceof LightReader reader)
            return reader.getDataLayerData(chunk, pos);

        return original.call(listener, pos);
    }
}
