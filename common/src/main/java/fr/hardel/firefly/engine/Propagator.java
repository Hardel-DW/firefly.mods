package fr.hardel.firefly.engine;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.Arrays;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

abstract class Propagator {
    protected static final Direction[] DIRECTIONS = Direction.values();
    private static final Direction[] OPPOSITES = Arrays.stream(DIRECTIONS).map(Direction::getOpposite).toArray(Direction[]::new);
    private static final Direction[] HORIZONTALS = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final int MAX_LEVEL = 15;
    private static final int UNKNOWN = -1;

    protected final LightWindow window;
    protected final LightWindow.Layer layer;
    private final IntOpenHashSet checks = new IntOpenHashSet();
    private final IntOpenHashSet rechecks = new IntOpenHashSet();
    private final LightQueue increases = new LightQueue();
    private final LightQueue decreases = new LightQueue();

    protected Propagator(LightWindow window, LightWindow.Layer layer) {
        this.window = window;
        this.layer = layer;
    }

    void check(int cell) {
        this.checks.add(cell);
    }

    void reconcileFaces(int chunkIndex) {
        for (Direction direction : HORIZONTALS) {
            int neighbor = LightWindow.neighborChunk(chunkIndex, direction);
            if (this.layer.enabled(neighbor)) {
                reconcileFace(chunkIndex, neighbor, direction);
            }
        }
    }

    void run() {
        IntIterator cells = this.checks.iterator();
        while (cells.hasNext()) {
            checkNode(cells.nextInt());
        }

        IntIterator rechecked = this.rechecks.iterator();
        while (rechecked.hasNext()) {
            recheckNode(rechecked.nextInt());
        }

        this.checks.clear();
        this.rechecks.clear();
        while (!this.decreases.isEmpty()) {
            propagateDecrease(this.decreases.next());
        }

        while (!this.increases.isEmpty()) {
            long entry = this.increases.next();
            int cell = LightQueue.cell(entry);
            int target = LightQueue.level(entry);
            int level = this.layer.get(cell);
            if (LightQueue.fromEmission(entry) && level < target) {
                this.layer.set(cell, target);
                level = target;
            }

            if (level == target) {
                propagateIncrease(cell, entry, level);
            }
        }

        this.layer.finish();
    }

    abstract void setLightEnabled(int chunkIndex, boolean enabled);

    abstract void propagateLightSources();

    protected abstract void checkNode(int cell);

    protected abstract void recheckNode(int cell);

    protected abstract int decreasedEmission(int cell);

    protected abstract int sourceLevel(int cell, BlockState state);

    protected abstract boolean readsThroughUnstored();

    protected abstract int emptySectionsBelow(int cell);

    protected abstract void spreadBelow(int cell, Direction direction, int level, boolean increase, int emptySectionsBelow);

    protected void increase(int cell, int level, int directions, boolean fromEmptyShape, boolean fromEmission) {
        this.increases.add(LightQueue.entry(cell, level, directions, fromEmptyShape, fromEmission));
    }

    protected void decrease(int cell, int level, int directions) {
        this.decreases.add(LightQueue.entry(cell, level, directions, false, false));
    }

    protected static boolean emptyShape(BlockState state) {
        return !state.canOcclude() || !state.useShapeForLightOcclusion();
    }

    private void reconcileFace(int chunkIndex, int neighbor, Direction direction) {
        for (int section = 0; section < this.window.sections(); section++) {
            if (this.layer.differs(chunkIndex, neighbor, section)) {
                reconcileSection(chunkIndex, section, direction);
            }
        }
    }

    private void reconcileSection(int chunkIndex, int section, Direction direction) {
        for (int localY = 0; localY < 16; localY++) {
            for (int along = 0; along < 16; along++) {
                int cell = this.window.faceCell(chunkIndex, section, direction, along, localY);
                int across = this.window.neighbor(cell, direction);
                reconcile(cell, across);
                reconcile(across, cell);
            }
        }
    }

    private void reconcile(int cell, int across) {
        if (!this.layer.stored(cell))
            return;

        int level = this.layer.get(cell);
        int acrossLevel = this.layer.level(across);
        if (level == 0 && acrossLevel >= 0 && acrossLevel <= 1)
            return;

        int expected = expectedLevel(cell);
        if (expected == UNKNOWN || expected < level) {
            this.rechecks.add(cell);
            return;
        }

        if (expected > level) {
            raise(cell, level);
        }
    }

    private int expectedLevel(int cell) {
        BlockState state = this.window.state(cell);
        int expected = sourceLevel(cell, state);
        if (expected == MAX_LEVEL)
            return expected;

        for (Direction direction : DIRECTIONS) {
            expected = this.window.hasNeighbor(cell, direction) ? expectedThrough(cell, state, direction, expected) : expected;
            if (expected == UNKNOWN)
                return UNKNOWN;
        }

        return expected;
    }

    private int expectedThrough(int cell, BlockState state, Direction direction, int expected) {
        int from = this.window.neighbor(cell, direction);
        int fromLevel = this.layer.level(from);
        if (fromLevel < 0)
            return readsThroughUnstored() ? UNKNOWN : expected;

        int level = fromLevel - opacity(state);
        return level > expected && !occludes(this.window.state(from), state, opposite(direction)) ? level : expected;
    }

    private void raise(int cell, int level) {
        BlockState state = this.window.state(cell);
        int source = sourceLevel(cell, state);
        if (source > level) {
            increase(cell, source, LightQueue.ALL_DIRECTIONS, emptyShape(state), true);
        }

        for (Direction direction : DIRECTIONS) {
            if (this.window.hasNeighbor(cell, direction)) {
                pull(this.window.neighbor(cell, direction), level, direction);
            }
        }
    }

    private void pull(int from, int level, Direction direction) {
        int fromLevel = this.layer.level(from);
        if (fromLevel > level + 1) {
            increase(from, fromLevel, LightQueue.only(opposite(direction)), emptyShape(this.window.state(from)), false);
        }
    }

    private void propagateIncrease(int from, long entry, int fromLevel) {
        BlockState fromState = null;
        int emptySectionsBelow = emptySectionsBelow(from);
        for (Direction direction : DIRECTIONS) {
            if (LightQueue.goes(entry, direction) && this.window.hasNeighbor(from, direction)) {
                fromState = increaseTowards(from, entry, fromLevel, direction, fromState, emptySectionsBelow);
            }
        }
    }

    private @Nullable BlockState increaseTowards(int from, long entry, int fromLevel, Direction direction, @Nullable BlockState fromState, int emptySectionsBelow) {
        int to = this.window.neighbor(from, direction);
        int oldLevel = this.layer.level(to);
        if (oldLevel < 0 || fromLevel - 1 <= oldLevel)
            return fromState;

        BlockState toState = this.window.state(to);
        int toLevel = fromLevel - opacity(toState);
        if (toLevel <= oldLevel)
            return fromState;

        BlockState sourceState = fromState != null ? fromState : LightQueue.fromEmptyShape(entry) ? Blocks.AIR.defaultBlockState() : this.window.state(from);
        if (occludes(sourceState, toState, direction))
            return sourceState;

        this.layer.set(to, toLevel);
        if (toLevel > 1) {
            increase(to, toLevel, LightQueue.allBut(opposite(direction)), emptyShape(toState), false);
        }

        spreadBelow(to, direction, toLevel, true, emptySectionsBelow);
        return sourceState;
    }

    private void propagateDecrease(long entry) {
        int from = LightQueue.cell(entry);
        int fromLevel = LightQueue.level(entry);
        int emptySectionsBelow = emptySectionsBelow(from);
        for (Direction direction : DIRECTIONS) {
            if (LightQueue.goes(entry, direction) && this.window.hasNeighbor(from, direction)) {
                decreaseTowards(from, fromLevel, direction, emptySectionsBelow);
            }
        }
    }

    private void decreaseTowards(int from, int fromLevel, Direction direction, int emptySectionsBelow) {
        int to = this.window.neighbor(from, direction);
        int toLevel = this.layer.level(to);
        if (toLevel <= 0)
            return;

        if (toLevel > fromLevel - 1) {
            increase(to, toLevel, LightQueue.only(opposite(direction)), false, false);
            return;
        }

        int toEmission = decreasedEmission(to);
        this.layer.set(to, 0);
        if (toEmission < toLevel) {
            decrease(to, toLevel, LightQueue.allBut(opposite(direction)));
        }

        if (toEmission > 0) {
            increase(to, toEmission, LightQueue.ALL_DIRECTIONS, emptyShape(this.window.state(to)), true);
        }

        spreadBelow(to, direction, toLevel, false, emptySectionsBelow);
    }

    protected static Direction opposite(Direction direction) {
        return OPPOSITES[direction.ordinal()];
    }

    private static int opacity(BlockState state) {
        return Math.max(1, state.getLightDampening());
    }

    private static boolean occludes(BlockState fromState, BlockState toState, Direction direction) {
        if (emptyShape(fromState) && emptyShape(toState))
            return false;

        VoxelShape fromShape = LightEngine.getOcclusionShape(fromState, direction);
        VoxelShape toShape = LightEngine.getOcclusionShape(toState, opposite(direction));
        return Shapes.faceShapeOccludes(fromShape, toShape);
    }
}
