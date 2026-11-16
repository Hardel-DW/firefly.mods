package fr.hardel.firefly.engine;

import fr.hardel.firefly.server.FireflyLightEngine;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.lighting.LevelLightEngine;

record VanillaParent(FireflyLightEngine engine) {
    void call(String method, Class<?> result, List<Class<?>> parameters, Object... arguments) {
        try {
            handle(method, result, parameters).invokeWithArguments(arguments);
        } catch (Throwable failure) {
            throw new AssertionError(failure);
        }
    }

    private MethodHandle handle(String method, Class<?> result, List<Class<?>> parameters) throws ReflectiveOperationException {
        return MethodHandles.privateLookupIn(ThreadedLevelLightEngine.class, MethodHandles.lookup())
            .findSpecial(LevelLightEngine.class, method, MethodType.methodType(result, parameters), ThreadedLevelLightEngine.class)
            .bindTo(this.engine);
    }
}
