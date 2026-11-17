package fr.hardel.firefly.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ServerLevel.class, priority = 1100)
public abstract class AxiomServerLevelMixin {
    @ModifyExpressionValue(method = "updateLighting", at = @At(value = "INVOKE", target = "Lcom/moulberry/axiom/Axiom;hasStarlight()Z"), require = 0)
    private boolean firefly$relightTheEditedChunks(boolean hasStarlight) {
        return true;
    }
}
