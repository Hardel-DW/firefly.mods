package fr.hardel.firefly.engine;

import java.util.ServiceLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

@FunctionalInterface
public interface LightEmission {
    LightEmission VANILLA = (state, _, _) -> state.getLightEmission();

    int emission(BlockState state, BlockGetter level, BlockPos pos);

    static LightEmission loaded() {
        return ServiceLoader.load(LightEmission.class, LightEmission.class.getClassLoader()).findFirst().orElse(VANILLA);
    }
}
