package fr.hardel.firefly.neoforge;

import fr.hardel.firefly.engine.LightEmission;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class NeoForgeLightEmission implements LightEmission {
    @Override
    public int emission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getLightEmission(level, pos);
    }

    @Override
    public boolean mayEmit(BlockState state) {
        return state.hasDynamicLightEmission() || state.getLightEmission() != 0;
    }
}
