package fr.hardel.firefly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.firefly.server.FireflyLightEngine;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkStatusTasks.class)
public abstract class ChunkStatusTasksMixin {
    @WrapOperation(method = "initializeLight", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;initializeLightSources()V"))
    private static void firefly$fillSourcesInTheLightTask(ChunkAccess chunk, Operation<Void> original, @Local(argsOnly = true) WorldGenContext context) {
        if (!(context.lightEngine() instanceof FireflyLightEngine)) {
            original.call(chunk);
        }
    }

    @WrapOperation(method = "initializeLight", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ThreadedLevelLightEngine;initializeLight(Lnet/minecraft/world/level/chunk/ChunkAccess;Z)Ljava/util/concurrent/CompletableFuture;"))
    private static CompletableFuture<ChunkAccess> firefly$initializeLightAhead(ThreadedLevelLightEngine engine, ChunkAccess chunk, boolean lighted,
        Operation<CompletableFuture<ChunkAccess>> original) {
        if (engine instanceof FireflyLightEngine firefly)
            return firefly.initializeLightAhead(chunk, lighted);

        return original.call(engine, chunk, lighted);
    }
}
