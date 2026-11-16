package fr.hardel.firefly.mixin;

import fr.hardel.firefly.server.FireflyLightEngine;
import java.util.function.IntSupplier;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ThreadedLevelLightEngine.class)
public abstract class ThreadedLevelLightEngineMixin {
    @ModifyVariable(method = "addTask(IILjava/util/function/IntSupplier;Lnet/minecraft/server/level/ThreadedLevelLightEngine$TaskType;Ljava/lang/Runnable;)V",
        at = @At("HEAD"), argsOnly = true)
    private Runnable firefly$postUpdateAfterTheLight(Runnable task, int chunkX, int chunkZ, IntSupplier level, ThreadedLevelLightEngine.TaskType type) {
        if (type == ThreadedLevelLightEngine.TaskType.POST_UPDATE && (Object) this instanceof FireflyLightEngine engine)
            return () -> engine.postUpdate(chunkX, chunkZ, task);

        return task;
    }
}
