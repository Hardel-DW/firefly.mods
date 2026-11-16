package fr.hardel.firefly.mixin;

import fr.hardel.firefly.server.FireflyLightEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelLightEngine.class)
public abstract class LevelLightEngineMixin {
    @Inject(method = "checkBlock", at = @At("HEAD"), cancellable = true)
    private void firefly$checkBlock(BlockPos pos, CallbackInfo callback) {
        if ((Object) this instanceof FireflyLightEngine engine) {
            engine.checkBlock(pos);
            callback.cancel();
        }
    }

    @Inject(method = "runLightUpdates", at = @At("HEAD"), cancellable = true)
    private void firefly$runLightUpdates(CallbackInfoReturnable<Integer> callback) {
        if ((Object) this instanceof FireflyLightEngine engine) {
            engine.submitUnsubmitted();
            callback.setReturnValue(0);
        }
    }

    @Inject(method = "updateSectionStatus", at = @At("HEAD"), cancellable = true)
    private void firefly$updateSectionStatus(SectionPos pos, boolean sectionEmpty, CallbackInfo callback) {
        if ((Object) this instanceof FireflyLightEngine engine) {
            engine.updateSectionStatus(pos, sectionEmpty);
            callback.cancel();
        }
    }

    @Inject(method = "setLightEnabled", at = @At("HEAD"), cancellable = true)
    private void firefly$setLightEnabled(ChunkPos pos, boolean enable, CallbackInfo callback) {
        if ((Object) this instanceof FireflyLightEngine engine) {
            engine.setLightEnabled(pos, enable);
            callback.cancel();
        }
    }

    @Inject(method = "propagateLightSources", at = @At("HEAD"), cancellable = true)
    private void firefly$propagateLightSources(ChunkPos pos, CallbackInfo callback) {
        if ((Object) this instanceof FireflyLightEngine engine) {
            engine.propagateLightSources(pos);
            callback.cancel();
        }
    }

    @Inject(method = "queueSectionData", at = @At("HEAD"), cancellable = true)
    private void firefly$queueSectionData(LightLayer layer, SectionPos pos, @Nullable DataLayer data, CallbackInfo callback) {
        if ((Object) this instanceof FireflyLightEngine engine) {
            engine.queueSectionData(layer, pos, data);
            callback.cancel();
        }
    }

    @Inject(method = "retainData", at = @At("HEAD"), cancellable = true)
    private void firefly$retainData(ChunkPos pos, boolean retain, CallbackInfo callback) {
        if ((Object) this instanceof FireflyLightEngine engine) {
            engine.retainData(pos, retain);
            callback.cancel();
        }
    }
}
