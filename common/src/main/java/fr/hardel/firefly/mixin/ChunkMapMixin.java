package fr.hardel.firefly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.firefly.engine.LightEmission;
import fr.hardel.firefly.schedule.LightExecutor;
import fr.hardel.firefly.server.FireflyLightEngine;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskDispatcher;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/server/level/ThreadedLevelLightEngine"))
    private ThreadedLevelLightEngine firefly$lightEngine(LightChunkGetter source, ChunkMap chunkMap, boolean hasSkyLight, ConsecutiveExecutor executor,
        ChunkTaskDispatcher dispatcher, Operation<ThreadedLevelLightEngine> original) {
        return new FireflyLightEngine(source, chunkMap, hasSkyLight, executor, dispatcher, LightEmission.loaded(), LightExecutor.loaded());
    }

    @WrapOperation(method = "lambda$scheduleUnload$0", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ThreadedLevelLightEngine;updateChunkStatus(Lnet/minecraft/world/level/ChunkPos;)V"))
    private void firefly$unloadTheChunkItself(ThreadedLevelLightEngine engine, ChunkPos pos, Operation<Void> original, @Local ChunkAccess chunk) {
        ((FireflyLightEngine) engine).unload(chunk);
    }
}
