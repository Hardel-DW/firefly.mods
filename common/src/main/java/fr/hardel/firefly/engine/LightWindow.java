package fr.hardel.firefly.engine;

import fr.hardel.firefly.storage.ChunkLight;
import fr.hardel.firefly.storage.LightColumn;
import fr.hardel.firefly.storage.Nibbles;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.jspecify.annotations.Nullable;

final class LightWindow {
    static final int RADIUS = 2;
    static final int CHUNKS = 2 * RADIUS + 1;
    static final int CENTER = RADIUS * CHUNKS + RADIUS;
    private static final int Z_SHIFT = 7;
    private static final int Y_SHIFT = 14;
    private static final int AXIS_MASK = (1 << Z_SHIFT) - 1;
    private static final BlockState ABSENT = Blocks.BEDROCK.defaultBlockState();

    final Layer block;
    final @Nullable Layer sky;
    private final LightChunkGetter source;
    private final LightEmission emission;
    private final ChunkSkyLightSources emptySources;
    private final int chunkX;
    private final int chunkZ;
    private final int minSection;
    private final int sections;
    private final int originX;
    private final int originZ;
    private final int minBlockY;
    private final @Nullable LightChunk[] chunks = new LightChunk[CHUNKS * CHUNKS];
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    LightWindow(LightChunkGetter source, LightEmission emission, ChunkSkyLightSources emptySources, boolean hasSky, int chunkX, int chunkZ, int minSection, int sections) {
        this.source = source;
        this.emission = emission;
        this.emptySources = emptySources;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minSection = minSection;
        this.sections = sections;
        this.originX = SectionPos.sectionToBlockCoord(chunkX - RADIUS);
        this.originZ = SectionPos.sectionToBlockCoord(chunkZ - RADIUS);
        this.minBlockY = SectionPos.sectionToBlockCoord(minSection);
        for (int dx = 0; dx < CHUNKS; dx++) {
            for (int dz = 0; dz < CHUNKS; dz++) {
                this.chunks[dx * CHUNKS + dz] = source.getChunkForLighting(chunkX - RADIUS + dx, chunkZ - RADIUS + dz);
            }
        }

        this.block = new Layer(LightLayer.BLOCK);
        this.sky = hasSky ? new Layer(LightLayer.SKY) : null;
    }

    int cell(int blockX, int blockY, int blockZ) {
        return blockX - this.originX | blockZ - this.originZ << Z_SHIFT | blockY - this.minBlockY << Y_SHIFT;
    }

    int blockX(int cell) {
        return this.originX + (cell & AXIS_MASK);
    }

    int blockY(int cell) {
        return this.minBlockY + (cell >>> Y_SHIFT);
    }

    int blockZ(int cell) {
        return this.originZ + (cell >>> Z_SHIFT & AXIS_MASK);
    }

    static int localX(int cell) {
        return cell & 15;
    }

    static int localY(int cell) {
        return cell >>> Y_SHIFT & 15;
    }

    static int localZ(int cell) {
        return cell >>> Z_SHIFT & 15;
    }

    static int chunkIndex(int cell) {
        return (cell >> 4 & 7) * CHUNKS + (cell >>> Z_SHIFT + 4 & 7);
    }

    static int sectionIndex(int cell) {
        return cell >>> Y_SHIFT + 4;
    }

    int sections() {
        return this.sections;
    }

    int minSection() {
        return this.minSection;
    }

    boolean insideHeight(int blockY) {
        return blockY >= this.minBlockY && blockY < this.minBlockY + (this.sections << 4);
    }

    int sectionIndexOfY(int blockY) {
        return blockY - this.minBlockY >> 4;
    }

    int sectionBottomY(int section) {
        return this.minBlockY + (section << 4);
    }

    int centerCell(int localX, int blockY, int localZ) {
        return cell(this.originX + (RADIUS << 4) + localX, blockY, this.originZ + (RADIUS << 4) + localZ);
    }

    boolean hasNeighbor(int cell, Direction direction) {
        return switch (direction) {
            case DOWN -> cell >>> Y_SHIFT != 0;
            case UP -> (cell >>> Y_SHIFT) + 1 < this.sections << 4;
            default -> true;
        };
    }

    static int neighbor(int cell, Direction direction) {
        return switch (direction) {
            case DOWN -> cell - (1 << Y_SHIFT);
            case UP -> cell + (1 << Y_SHIFT);
            case NORTH -> cell - (1 << Z_SHIFT);
            case SOUTH -> cell + (1 << Z_SHIFT);
            case WEST -> cell - 1;
            case EAST -> cell + 1;
        };
    }

    static int withY(int cell, int sectionIndex, int localY) {
        return cell & (1 << Y_SHIFT) - 1 | (sectionIndex << 4 | localY) << Y_SHIFT;
    }

    BlockState state(int cell) {
        LightChunk chunk = this.chunks[chunkIndex(cell)];
        return chunk == null ? ABSENT : chunk.getBlockState(this.cursor.set(blockX(cell), blockY(cell), blockZ(cell)));
    }

    int emission(int cell, BlockState state) {
        return this.emission.emission(state, this.source.getLevel(), this.cursor.set(blockX(cell), blockY(cell), blockZ(cell)));
    }

    LightChunk center() {
        LightChunk center = this.chunks[CENTER];
        if (center == null)
            throw new IllegalStateException("A light task runs on chunk %d %d, which is not loaded".formatted(this.chunkX, this.chunkZ));

        return center;
    }

    @Nullable ChunkSkyLightSources sources(int chunkIndex) {
        LightChunk chunk = this.chunks[chunkIndex];
        return chunk == null ? null : chunk.getSkyLightSources();
    }

    ChunkSkyLightSources sourcesOrEmpty(int chunkIndex) {
        ChunkSkyLightSources sources = sources(chunkIndex);
        return sources == null ? this.emptySources : sources;
    }

    int lowestSourceY(int blockX, int blockZ, int absent) {
        ChunkSkyLightSources sources = sources(chunkIndex(cell(blockX, this.minBlockY, blockZ)));
        return sources == null ? absent : sources.getLowestSourceY(SectionPos.sectionRelative(blockX), SectionPos.sectionRelative(blockZ));
    }

    void sectionStatus(int section, boolean empty) {
        LightColumn center = column(CENTER);
        if (center.nonEmpty(section) == !empty)
            return;

        center.setNonEmpty(section, !empty);
        updateStored(CENTER, section);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean around = dx != 0 || dy != 0 || dz != 0;
                    int target = section + dy;
                    if (around && target >= 0 && target < this.sections) {
                        updateStored(CENTER + dx * CHUNKS + dz, target);
                    }
                }
            }
        }
    }

    void publish() {
        this.block.publish();
        if (this.sky != null) {
            this.sky.publish();
        }
    }

    private void updateStored(int chunkIndex, int section) {
        if (this.chunks[chunkIndex] == null)
            return;

        boolean stored = nonEmptyAround(chunkIndex, section);
        this.block.updateStored(chunkIndex, section, stored);
        if (this.sky != null) {
            this.sky.updateStored(chunkIndex, section, stored);
        }
    }

    private boolean nonEmptyAround(int chunkIndex, int section) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LightColumn column = columnIfPresent(chunkIndex + dx * CHUNKS + dz);
                if (column != null && nonEmptyAt(column, section)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean nonEmptyAt(LightColumn column, int section) {
        return column.nonEmpty(section) || section > 0 && column.nonEmpty(section - 1) || section + 1 < this.sections && column.nonEmpty(section + 1);
    }

    private LightColumn column(int chunkIndex) {
        return ((ChunkLight) this.chunks[chunkIndex]).firefly$light();
    }

    private @Nullable LightColumn columnIfPresent(int chunkIndex) {
        LightChunk chunk = this.chunks[chunkIndex];
        return chunk == null ? null : ((ChunkLight) chunk).firefly$lightIfPresent();
    }

    private long sectionNode(int chunkIndex, int section) {
        return SectionPos.asLong(this.chunkX - RADIUS + chunkIndex / CHUNKS, this.minSection + section, this.chunkZ - RADIUS + chunkIndex % CHUNKS);
    }

    final class Layer {
        private final LightLayer type;
        private final byte[][] current;
        private final boolean[] loaded;
        private final boolean[] owned;
        private final boolean[] removing;
        private final boolean[] changed;
        private final IntArrayList changes = new IntArrayList();
        private final LongOpenHashSet affected = new LongOpenHashSet();

        private Layer(LightLayer type) {
            int slots = CHUNKS * CHUNKS * LightWindow.this.sections;
            this.type = type;
            this.current = new byte[slots][];
            this.loaded = new boolean[slots];
            this.owned = new boolean[slots];
            this.removing = new boolean[slots];
            this.changed = new boolean[slots];
        }

        boolean stored(int cell) {
            return data(slot(cell)) != null;
        }

        boolean stored(int chunkIndex, int section) {
            return data(chunkIndex * LightWindow.this.sections + section) != null;
        }

        int get(int cell) {
            return Nibbles.get(data(slot(cell)), Nibbles.index(localX(cell), localY(cell), localZ(cell)));
        }

        void set(int cell, int level) {
            write(cell, level);
            SectionPos.aroundAndAtBlockPos(blockX(cell), blockY(cell), blockZ(cell), this.affected::add);
        }

        void write(int cell, int level) {
            int slot = slot(cell);
            byte[] data = data(slot);
            if (!this.owned[slot]) {
                data = data.clone();
                this.current[slot] = data;
                this.owned[slot] = true;
                markChanged(slot);
            }

            Nibbles.set(data, Nibbles.index(localX(cell), localY(cell), localZ(cell)), level);
        }

        boolean empty(int chunkIndex, int section) {
            return data(chunkIndex * LightWindow.this.sections + section) == Nibbles.ZERO;
        }

        void fill(int chunkIndex, int section, byte[] data) {
            int slot = chunkIndex * LightWindow.this.sections + section;
            this.current[slot] = data;
            this.owned[slot] = false;
            markChanged(slot);
        }

        boolean enabled(int chunkIndex) {
            LightColumn column = columnIfPresent(chunkIndex);
            return column != null && column.sections(this.type).enabled();
        }

        void setEnabled(int chunkIndex, boolean enabled) {
            column(chunkIndex).sections(this.type).setEnabled(enabled);
        }

        int topSection(int chunkIndex) {
            for (int section = LightWindow.this.sections - 1; section >= 0; section--) {
                if (stored(chunkIndex, section)) {
                    return section;
                }
            }

            return -1;
        }

        void finishRemovals() {
            for (int slot = 0; slot < this.removing.length; slot++) {
                if (this.removing[slot]) {
                    this.removing[slot] = false;
                    this.current[slot] = null;
                    this.owned[slot] = false;
                    markChanged(slot);
                }
            }
        }

        private void updateStored(int chunkIndex, int section, boolean stored) {
            int slot = chunkIndex * LightWindow.this.sections + section;
            boolean present = data(slot) != null && !this.removing[slot];
            if (stored == present)
                return;

            if (!stored) {
                this.removing[slot] = true;
                return;
            }

            if (this.removing[slot]) {
                this.removing[slot] = false;
                return;
            }

            byte[] data = this.type == LightLayer.BLOCK ? Nibbles.ZERO : skyData(chunkIndex, section);
            this.current[slot] = data;
            this.owned[slot] = !Nibbles.shared(data);
            markChanged(slot);
            affectAround(chunkIndex, section);
        }

        private byte[] skyData(int chunkIndex, int section) {
            int top = topSection(chunkIndex);
            if (top > section) {
                int above = section + 1;
                while (!stored(chunkIndex, above)) {
                    above++;
                }

                return Nibbles.bottomLayerRepeated(data(chunkIndex * LightWindow.this.sections + above));
            }

            return enabled(chunkIndex) ? Nibbles.FULL : Nibbles.ZERO;
        }

        private void affectAround(int chunkIndex, int section) {
            long node = sectionNode(chunkIndex, section);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        this.affected.add(SectionPos.offset(node, dx, dy, dz));
                    }
                }
            }
        }

        private byte @Nullable [] data(int slot) {
            if (!this.loaded[slot]) {
                LightColumn column = columnIfPresent(slot / LightWindow.this.sections);
                this.current[slot] = column == null ? null : column.sections(this.type).get(slot % LightWindow.this.sections);
                this.loaded[slot] = true;
            }

            return this.current[slot];
        }

        private void markChanged(int slot) {
            if (!this.changed[slot]) {
                this.changed[slot] = true;
                this.changes.add(slot);
            }
        }

        private void publish() {
            for (int index = 0; index < this.changes.size(); index++) {
                int slot = this.changes.getInt(index);
                column(slot / LightWindow.this.sections).sections(this.type).publish(slot % LightWindow.this.sections, this.current[slot]);
            }

            LongIterator nodes = this.affected.iterator();
            while (nodes.hasNext()) {
                LightWindow.this.source.onLightUpdate(this.type, SectionPos.of(nodes.nextLong()));
            }
        }

        private int slot(int cell) {
            return chunkIndex(cell) * LightWindow.this.sections + sectionIndex(cell);
        }
    }
}
