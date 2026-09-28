package fr.hardel.firefly.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.world.AuxiliaryLightManager;

final class AuxiliaryLamp extends Block {
    AuxiliaryLamp(Properties properties) {
        super(properties);
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        AuxiliaryLightManager lights = level.getAuxLightManager(pos);
        return lights == null ? 0 : lights.getLightAt(pos);
    }

    @Override
    public boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }
}
