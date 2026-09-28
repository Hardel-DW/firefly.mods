package fr.hardel.firefly.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

@FunctionalInterface
public interface LightEmission {
    int emission(BlockState state, BlockGetter level, BlockPos pos);
}
