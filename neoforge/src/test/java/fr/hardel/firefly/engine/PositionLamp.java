package fr.hardel.firefly.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class PositionLamp extends Block {
    private static final int LEVELS = 16;

    PositionLamp(Properties properties) {
        super(properties);
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return Math.floorMod(pos.getX() * 3 + pos.getY() * 5 + pos.getZ() * 7, LEVELS);
    }

    @Override
    public boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }
}
