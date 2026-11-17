package fr.hardel.firefly.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.packets.AxiomServerboundSetBuffer")
public abstract class AxiomSetBufferMixin {
    @ModifyExpressionValue(method = "applyBlockBufferServer", at = @At(value = "INVOKE", target = "Lcom/moulberry/axiom/Axiom;hasStarlight()Z"))
    private static boolean firefly$relightTheEditedChunks(boolean hasStarlight) {
        return true;
    }
}
