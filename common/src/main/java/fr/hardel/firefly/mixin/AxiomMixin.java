package fr.hardel.firefly.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.Axiom")
public abstract class AxiomMixin {
    @Inject(method = "hasStarlight", at = @At("HEAD"), cancellable = true)
    private static void firefly$relightChunksAfterAnEdit(CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(true);
    }
}
