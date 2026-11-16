package fr.hardel.firefly.mixin;

import fr.hardel.firefly.server.FireflyLightEngine;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.utils.StarlightHelper")
public abstract class AxiomStarlightHelperMixin {
    @Inject(method = "relightChunks", at = @At("HEAD"), cancellable = true)
    private static void firefly$relightChunks(ThreadedLevelLightEngine engine, Set<ChunkPos> chunks, Consumer<ChunkPos> chunkLightCallback, IntConsumer onComplete,
        CallbackInfo callback) {
        ((FireflyLightEngine) engine).relightChunks(chunks, chunkLightCallback, onComplete);
        callback.cancel();
    }
}
