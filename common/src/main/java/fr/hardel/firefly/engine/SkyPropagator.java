package fr.hardel.firefly.engine;

import fr.hardel.firefly.storage.Nibbles;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;

final class SkyPropagator extends Propagator {
    private static final int SOURCE = 15;
    private static final int BELOW_AND_SIDES = LightQueue.allBut(Direction.UP);
    private static final int GRID = 18;
    private static final int COLUMN_STEP = 256;

    private final int[] lowestSources = new int[GRID * GRID];
    private int highestSourceY;

    SkyPropagator(LightWindow window, LightWindow.Layer layer) {
        super(window, layer);
    }

    @Override
    void setLightEnabled(int chunkIndex, boolean enabled) {
        this.layer.setEnabled(chunkIndex, enabled);
        if (!enabled)
            return;

        int highestNonSourceY = this.window.sourcesOrEmpty(chunkIndex).getHighestLowestSourceY() - 1;
        int bottom = Math.max(0, SectionPos.blockToSectionCoord(highestNonSourceY) + 1 - this.window.minSection());
        for (int section = this.layer.topSection(chunkIndex); section >= bottom; section--) {
            if (this.layer.stored(chunkIndex, section) && this.layer.empty(chunkIndex, section)) {
                this.layer.fill(chunkIndex, section, Nibbles.FULL);
            }
        }
    }

    @Override
    void propagateLightSources() {
        this.layer.setEnabled(LightWindow.CENTER, true);
        readSources();
        boolean sourcesBelow = true;
        for (int section = this.layer.topSection(LightWindow.CENTER); section >= 0 && sourcesBelow; section--) {
            if (this.layer.stored(LightWindow.CENTER, section)) {
                sourcesBelow = lightSection(section);
            }
        }
    }

    @Override
    protected void checkNode(int cell) {
        int x = this.window.blockX(cell);
        int y = this.window.blockY(cell);
        int z = this.window.blockZ(cell);
        int lowestSourceY = this.layer.enabled(this.window.chunkIndex(cell)) ? this.window.lowestSourceY(x, z, Integer.MAX_VALUE) : Integer.MAX_VALUE;
        if (lowestSourceY != Integer.MAX_VALUE) {
            removeSourcesBelow(cell, lowestSourceY);
            addSourcesAbove(cell, lowestSourceY);
        }

        if (!this.layer.stored(cell))
            return;

        if (y >= lowestSourceY) {
            decrease(cell, SOURCE, BELOW_AND_SIDES);
            increase(cell, SOURCE, BELOW_AND_SIDES, false, false);
            return;
        }

        int oldLevel = this.layer.get(cell);
        if (oldLevel > 0) {
            this.layer.set(cell, 0);
        }

        decrease(cell, oldLevel > 0 ? oldLevel : 1, LightQueue.ALL_DIRECTIONS);
    }

    @Override
    protected int decreasedEmission(int cell) {
        return 0;
    }

    @Override
    protected int emptySectionsBelow(int cell) {
        if (LightWindow.localY(cell) != 0)
            return 0;

        int localX = LightWindow.localX(cell);
        int localZ = LightWindow.localZ(cell);
        if (localX != 0 && localX != 15 && localZ != 0 && localZ != 15)
            return 0;

        int chunk = this.window.chunkIndex(cell);
        int section = this.window.sectionIndex(cell);
        int empty = 0;
        while (section - empty - 1 >= 0 && !this.layer.stored(chunk, section - empty - 1)) {
            empty++;
        }

        return empty;
    }

    @Override
    protected void spreadBelow(int cell, Direction direction, int level, boolean increase, int emptySectionsBelow) {
        if (emptySectionsBelow == 0 || !crossedSectionEdge(direction, LightWindow.localX(cell), LightWindow.localZ(cell)))
            return;

        int chunk = this.window.chunkIndex(cell);
        int top = this.window.sectionIndex(cell) - 1;
        for (int section = top; section > top - emptySectionsBelow; section--) {
            if (this.layer.stored(chunk, section)) {
                spreadInSection(cell, section, direction, level, increase);
            }
        }
    }

    private void spreadInSection(int cell, int section, Direction direction, int level, boolean increase) {
        for (int localY = 15; localY >= 0; localY--) {
            int target = this.window.withY(cell, section, localY);
            this.layer.set(target, increase ? level : 0);
            if (increase && level > 1) {
                increase(target, level, LightQueue.allBut(opposite(direction)), true, false);
            }

            if (!increase) {
                decrease(target, level, LightQueue.allBut(opposite(direction)));
            }
        }
    }

    private void removeSourcesBelow(int cell, int lowestSourceY) {
        if (lowestSourceY <= this.window.sectionBottomY(0))
            return;

        int chunk = this.window.chunkIndex(cell);
        int startY = lowestSourceY - 1;
        for (int section = this.window.sectionIndexOfY(startY); section >= 0; section--) {
            if (this.layer.stored(chunk, section) && !removeSourcesInSection(cell, section, startY, lowestSourceY)) {
                return;
            }
        }
    }

    private boolean removeSourcesInSection(int cell, int section, int startY, int lowestSourceY) {
        int sectionBottomY = this.window.sectionBottomY(section);
        for (int y = Math.min(sectionBottomY + 15, startY); y >= sectionBottomY; y--) {
            int target = this.window.withY(cell, section, y - sectionBottomY);
            if (this.layer.get(target) != SOURCE)
                return false;

            this.layer.set(target, 0);
            decrease(target, SOURCE, y == lowestSourceY - 1 ? LightQueue.ALL_DIRECTIONS : BELOW_AND_SIDES);
        }

        return true;
    }

    private void addSourcesAbove(int cell, int lowestSourceY) {
        int x = this.window.blockX(cell);
        int z = this.window.blockZ(cell);
        int neighborLowestSourceY = Math.max(
            Math.max(this.window.lowestSourceY(x - 1, z, Integer.MIN_VALUE), this.window.lowestSourceY(x + 1, z, Integer.MIN_VALUE)),
            Math.max(this.window.lowestSourceY(x, z - 1, Integer.MIN_VALUE), this.window.lowestSourceY(x, z + 1, Integer.MIN_VALUE)));
        int chunk = this.window.chunkIndex(cell);
        int startY = Math.max(lowestSourceY, this.window.sectionBottomY(0));
        int top = this.layer.topSection(chunk);
        for (int section = this.window.sectionIndexOfY(startY); section <= top; section++) {
            if (this.layer.stored(chunk, section) && !addSourcesInSection(cell, section, startY, lowestSourceY, neighborLowestSourceY)) {
                return;
            }
        }
    }

    private boolean addSourcesInSection(int cell, int section, int startY, int lowestSourceY, int neighborLowestSourceY) {
        int sectionBottomY = this.window.sectionBottomY(section);
        for (int y = Math.max(sectionBottomY, startY); y <= sectionBottomY + 15; y++) {
            int target = this.window.withY(cell, section, y - sectionBottomY);
            if (this.layer.get(target) == SOURCE)
                return false;

            this.layer.set(target, SOURCE);
            if (y < neighborLowestSourceY || y == lowestSourceY) {
                increase(target, SOURCE, BELOW_AND_SIDES, false, false);
            }
        }

        return true;
    }

    private void readSources() {
        ChunkSkyLightSources center = this.window.sourcesOrEmpty(LightWindow.CENTER);
        ChunkSkyLightSources north = this.window.sourcesOrEmpty(LightWindow.CENTER - 1);
        ChunkSkyLightSources south = this.window.sourcesOrEmpty(LightWindow.CENTER + 1);
        ChunkSkyLightSources west = this.window.sourcesOrEmpty(LightWindow.CENTER - LightWindow.CHUNKS);
        ChunkSkyLightSources east = this.window.sourcesOrEmpty(LightWindow.CENTER + LightWindow.CHUNKS);
        this.highestSourceY = Integer.MIN_VALUE;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int lowestSourceY = center.getLowestSourceY(x, z);
                this.lowestSources[(x + 1) * GRID + z + 1] = lowestSourceY;
                this.highestSourceY = Math.max(this.highestSourceY, lowestSourceY);
            }

            this.lowestSources[(x + 1) * GRID] = north.getLowestSourceY(x, 15);
            this.lowestSources[(x + 1) * GRID + GRID - 1] = south.getLowestSourceY(x, 0);
            this.lowestSources[x + 1] = west.getLowestSourceY(15, x);
            this.lowestSources[(GRID - 1) * GRID + x + 1] = east.getLowestSourceY(0, x);
        }
    }

    private int lowestSourceY(int x, int z) {
        return this.lowestSources[(x + 1) * GRID + z + 1];
    }

    private boolean lightSection(int section) {
        int sectionMinY = this.window.sectionBottomY(section);
        int sectionMaxY = sectionMinY + 15;
        boolean filled = this.highestSourceY <= sectionMinY;
        if (filled) {
            this.layer.fill(LightWindow.CENTER, section, Nibbles.FULL);
        }

        boolean sourcesBelow = false;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int lowestSourceY = lowestSourceY(x, z);
                if (lowestSourceY <= sectionMaxY) {
                    lightColumn(x, z, sectionMinY, sectionMaxY, lowestSourceY, filled);
                }

                sourcesBelow |= lowestSourceY < sectionMinY;
            }
        }

        return sourcesBelow;
    }

    private void lightColumn(int x, int z, int sectionMinY, int sectionMaxY, int lowestSourceY, boolean filled) {
        int north = lowestSourceY(x, z - 1);
        int south = lowestSourceY(x, z + 1);
        int west = lowestSourceY(x - 1, z);
        int east = lowestSourceY(x + 1, z);
        int neighborLowestSourceY = Math.max(Math.max(north, south), Math.max(west, east));
        int top = filled ? Math.min(sectionMaxY, Math.max(neighborLowestSourceY - 1, lowestSourceY)) : sectionMaxY;
        int cell = this.window.centerCell(x, top, z) + COLUMN_STEP;
        for (int y = top; y >= Math.max(sectionMinY, lowestSourceY); y--) {
            cell -= COLUMN_STEP;
            if (!filled) {
                this.layer.write(cell, SOURCE);
            }

            if (y == lowestSourceY || y < neighborLowestSourceY) {
                increase(cell, SOURCE, directions(y == lowestSourceY, y < north, y < south, y < west, y < east), false, false);
            }
        }
    }

    private static int directions(boolean down, boolean north, boolean south, boolean west, boolean east) {
        int directions = down ? LightQueue.only(Direction.DOWN) : 0;
        directions |= north ? LightQueue.only(Direction.NORTH) : 0;
        directions |= south ? LightQueue.only(Direction.SOUTH) : 0;
        directions |= west ? LightQueue.only(Direction.WEST) : 0;
        return directions | (east ? LightQueue.only(Direction.EAST) : 0);
    }

    private static boolean crossedSectionEdge(Direction direction, int localX, int localZ) {
        return switch (direction) {
            case NORTH -> localZ == 15;
            case SOUTH -> localZ == 0;
            case WEST -> localX == 15;
            case EAST -> localX == 0;
            default -> false;
        };
    }
}
