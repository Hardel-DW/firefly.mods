package fr.hardel.firefly.engine;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

final class BlockPropagator extends Propagator {
    BlockPropagator(LightWindow window) {
        super(window, window.block);
    }

    @Override
    void setLightEnabled(int chunkIndex, boolean enabled) {
        this.layer.setEnabled(chunkIndex, enabled);
    }

    @Override
    void propagateLightSources() {
        this.layer.setEnabled(LightWindow.CENTER, true);
        this.window.center().findBlockLightSources((pos, state) -> {
            int cell = this.window.cell(pos.getX(), pos.getY(), pos.getZ());
            increase(cell, this.window.emission(cell, state), LightQueue.ALL_DIRECTIONS, emptyShape(state), true);
        });
    }

    @Override
    protected void checkNode(int cell) {
        if (!this.layer.stored(cell))
            return;

        BlockState state = this.window.state(cell);
        int emission = emission(cell, state);
        int oldLevel = this.layer.get(cell);
        if (emission < oldLevel) {
            this.layer.set(cell, 0);
        }

        decrease(cell, emission < oldLevel ? oldLevel : 1, LightQueue.ALL_DIRECTIONS);
        if (emission > 0) {
            increase(cell, emission, LightQueue.ALL_DIRECTIONS, emptyShape(state), true);
        }
    }

    @Override
    protected void recheckNode(int cell) {
        checkNode(cell);
    }

    @Override
    protected int decreasedEmission(int cell) {
        return emission(cell, this.window.state(cell));
    }

    @Override
    protected int sourceLevel(int cell, BlockState state) {
        return emission(cell, state);
    }

    @Override
    protected boolean readsThroughUnstored() {
        return false;
    }

    @Override
    protected int emptySectionsBelow(int cell) {
        return 0;
    }

    @Override
    protected void spreadBelow(int cell, Direction direction, int level, boolean increase, int emptySectionsBelow) {
    }

    private int emission(int cell, BlockState state) {
        int emission = this.window.emission(cell, state);
        return emission > 0 && this.layer.enabled(this.window.chunkIndex(cell)) ? emission : 0;
    }
}
