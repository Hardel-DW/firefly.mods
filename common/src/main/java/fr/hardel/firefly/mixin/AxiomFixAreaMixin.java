package fr.hardel.firefly.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.packets.AxiomServerboundFixArea$Updater")
public abstract class AxiomFixAreaMixin {
    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/moulberry/axiom/Axiom;hasStarlight()Z"))
    private boolean firefly$relightTheFixedChunks(boolean hasStarlight) {
        return true;
    }
}
