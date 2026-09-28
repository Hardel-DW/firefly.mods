package fr.hardel.firefly.mixin;

import fr.hardel.firefly.storage.ChunkLight;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Inject(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V",
        at = @At("TAIL"))
    private void firefly$keepProtoLight(ServerLevel level, ProtoChunk protoChunk, LevelChunk.@Nullable PostLoadProcessor postLoad, CallbackInfo callback) {
        ((ChunkLight) this).firefly$setLight(((ChunkLight) protoChunk).firefly$light());
    }
}
