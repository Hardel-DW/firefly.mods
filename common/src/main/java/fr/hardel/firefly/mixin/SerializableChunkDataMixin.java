package fr.hardel.firefly.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import fr.hardel.firefly.server.FireflyLightEngine;
import fr.hardel.firefly.server.LightReader;
import fr.hardel.firefly.storage.Nibbles;
import fr.hardel.firefly.storage.PendingLight;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;
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

    @WrapOperation(method = "read", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;retainData(Lnet/minecraft/world/level/ChunkPos;Z)V"))
    private void firefly$keepTheSavedLight(LevelLightEngine engine, ChunkPos pos, boolean retain, Operation<Void> original,
        @Share("saved") LocalRef<PendingLight> saved) {
        saved.set(new PendingLight(engine.getLightSectionCount()));
    }

    @WrapOperation(method = "read", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;queueSectionData(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/DataLayer;)V"))
    private void firefly$keepTheSavedSection(LevelLightEngine engine, LightLayer layer, SectionPos pos, DataLayer data, Operation<Void> original,
        @Share("saved") LocalRef<PendingLight> saved) {
        saved.get().put(layer, pos.y() - engine.getMinLightSection(), Nibbles.of(data));
    }

    @ModifyReturnValue(method = "read", at = @At("RETURN"))
    private ProtoChunk firefly$giveTheSavedLightToTheChunk(ProtoChunk chunk, @Local(argsOnly = true) ServerLevel level, @Share("saved") LocalRef<PendingLight> saved) {
        ((FireflyLightEngine) level.getChunkSource().getLightEngine()).readFromDisk(chunk, saved.get());
        return chunk;
    }
}
