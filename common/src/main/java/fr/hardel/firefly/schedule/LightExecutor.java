package fr.hardel.firefly.schedule;

import fr.hardel.firefly.FireflyConfig;
import java.util.ServiceLoader;
import net.minecraft.world.level.chunk.LightChunkGetter;

public interface LightExecutor {
    int RADIUS = 1;

    void execute(LightChunkGetter level, int chunkX, int chunkZ, Runnable task);

    static LightExecutor loaded() {
        if (!FireflyConfig.get().sharedThreads())
            return ReservingExecutor.shared();

        return ServiceLoader.load(LightExecutor.class, LightExecutor.class.getClassLoader()).findFirst().orElseGet(ReservingExecutor::shared);
    }
}
