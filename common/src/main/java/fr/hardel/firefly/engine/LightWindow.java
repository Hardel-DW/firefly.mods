package fr.hardel.firefly.engine;

import fr.hardel.firefly.storage.ChunkLight;
import fr.hardel.firefly.storage.LightColumn;
import fr.hardel.firefly.storage.Nibbles;
import fr.hardel.firefly.storage.QueuedSections;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
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
    private static final BlockState OUTSIDE = Blocks.AIR.defaultBlockState();
    private static final int[] OFFSETS = Arrays.stream(Direction.values())
        .mapToInt(direction -> direction.getStepX() + (direction.getStepZ() << Z_SHIFT) + (direction.getStepY() << Y_SHIFT))
        .toArray();

    final Layer block;
    final @Nullable Layer sky;
    private final LightChunkGetter source;
    private final LightEmission emission;
    private final QueuedSections queued;
    private final ChunkSkyLightSources emptySources;
    private final int chunkX;
    private final int chunkZ;
    private final int minSection;
    private final int sections;
    private final int originX;
    private final int originZ;
    private final int minBlockY;
    private final @Nullable LightChunk[] chunks = new LightChunk[CHUNKS * CHUNKS];
    private final LevelChunkSection[][] blocks = new LevelChunkSection[CHUNKS * CHUNKS][];
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    LightWindow(LightChunkGetter source, LightEmission emission, QueuedSections queued, ChunkSkyLightSources emptySources, boolean hasSky, int chunkX, int chunkZ, int minSection,
        int sections) {
        this.source = source;
        this.emission = emission;
        this.queued = queued;
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
                LightChunk chunk = source.getChunkForLighting(chunkX - RADIUS + dx, chunkZ - RADIUS + dz);
                this.chunks[dx * CHUNKS + dz] = chunk;
                this.blocks[dx * CHUNKS + dz] = chunk == null ? null : ((ChunkAccess) chunk).getSections();
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
        if (direction == Direction.DOWN)
            return cell >>> Y_SHIFT != 0;

        return direction != Direction.UP || (cell >>> Y_SHIFT) + 1 < this.sections << 4;
    }

    static int neighbor(int cell, Direction direction) {
        return cell + OFFSETS[direction.ordinal()];
    }

    static int withY(int cell, int sectionIndex, int localY) {
        return cell & (1 << Y_SHIFT) - 1 | (sectionIndex << 4 | localY) << Y_SHIFT;
    }

    BlockState state(int cell) {
        LevelChunkSection[] sections = this.blocks[chunkIndex(cell)];
        if (sections == null)
            return ABSENT;

        int section = sectionIndex(cell) - 1;
        return section < 0 || section >= sections.length ? OUTSIDE : sections[section].getBlockState(localX(cell), localY(cell), localZ(cell));
    }

    int emission(int cell, BlockState state) {
        return this.emission.emission(state, this.source.getLevel(), this.cursor.set(blockX(cell), blockY(cell), blockZ(cell)));
    }

    boolean hasCenter() {
        return this.chunks[CENTER] != null;
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

    void unload() {
        LightColumn center = columnIfPresent(CENTER);
        if (center != null) {
            clearCenter(center);
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int section = 0; section < this.sections; section++) {
                    updateStored(CENTER + dx * CHUNKS + dz, section);
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

    private void clearCenter(LightColumn center) {
        for (int section = 0; section < this.sections; section++) {
            center.setNonEmpty(section, false);
        }

        center.sections(LightLayer.BLOCK).setEnabled(false);
        center.sections(LightLayer.SKY).setEnabled(false);
    }

    private LightColumn column(int chunkIndex) {
        return ((ChunkLight) this.chunks[chunkIndex]).firefly$light();
    }

    private @Nullable LightColumn columnIfPresent(int chunkIndex) {
        LightChunk chunk = this.chunks[chunkIndex];
        return chunk == null ? null : ((ChunkLight) chunk).firefly$light();
    }

    private int chunkX(int chunkIndex) {
        return this.chunkX - RADIUS + chunkIndex / CHUNKS;
    }

    private int chunkZ(int chunkIndex) {
        return this.chunkZ - RADIUS + chunkIndex % CHUNKS;
    }

    private long sectionNode(int chunkIndex, int section) {
        return SectionPos.asLong(chunkX(chunkIndex), this.minSection + section, chunkZ(chunkIndex));
    }

    final class Layer {
        private final LightLayer type;
        private final byte[][] current;
        private final boolean[] owned;
        private final boolean[] removing;
        private final boolean[] changed;
        private final boolean[] affectedInside;
        private final IntArrayList changes = new IntArrayList();
        private final LongOpenHashSet affected = new LongOpenHashSet();
        private final LongConsumer affect = this.affected::add;

        private Layer(LightLayer type) {
            int slots = CHUNKS * CHUNKS * LightWindow.this.sections;
            this.type = type;
            this.current = new byte[slots][];
            this.owned = new boolean[slots];
            this.removing = new boolean[slots];
            this.changed = new boolean[slots];
            this.affectedInside = new boolean[slots];
            for (int chunkIndex = 0; chunkIndex < CHUNKS * CHUNKS; chunkIndex++) {
                LightColumn column = columnIfPresent(chunkIndex);
                for (int section = 0; column != null && section < LightWindow.this.sections; section++) {
                    this.current[chunkIndex * LightWindow.this.sections + section] = column.sections(type).get(section);
                }
            }
        }

        int level(int cell) {
            byte[] data = this.current[slot(cell)];
            return data == null ? -1 : Nibbles.get(data, Nibbles.index(localX(cell), localY(cell), localZ(cell)));
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
            int x = localX(cell);
            int y = localY(cell);
            int z = localZ(cell);
            if (x == 0 || x == 15 || y == 0 || y == 15 || z == 0 || z == 15) {
                SectionPos.aroundAndAtBlockPos(blockX(cell), blockY(cell), blockZ(cell), this.affect);
                return;
            }

            int slot = slot(cell);
            if (!this.affectedInside[slot]) {
                this.affectedInside[slot] = true;
                this.affected.add(sectionNode(slot / LightWindow.this.sections, slot % LightWindow.this.sections));
            }
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

        void finish() {
            for (int slot = 0; slot < this.removing.length; slot++) {
                if (this.removing[slot]) {
                    remove(slot);
                }
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    applyQueued(CENTER + dx * CHUNKS + dz);
                }
            }
        }

        private void remove(int slot) {
            int chunkIndex = slot / LightWindow.this.sections;
            int section = slot % LightWindow.this.sections;
            QueuedSections.Column column = LightWindow.this.queued.column(chunkX(chunkIndex), chunkZ(chunkIndex));
            byte[] queuedData = column == null ? null : column.take(this.type, section);
            if (column != null && column.retained()) {
                column.put(this.type, section, queuedData != null ? queuedData : this.current[slot]);
            }

            this.removing[slot] = false;
            this.current[slot] = null;
            this.owned[slot] = false;
            markChanged(slot);
        }

        private void applyQueued(int chunkIndex) {
            QueuedSections.Column column = LightWindow.this.chunks[chunkIndex] == null ? null : LightWindow.this.queued.column(chunkX(chunkIndex), chunkZ(chunkIndex));
            if (column == null)
                return;

            for (int section = 0; section < LightWindow.this.sections; section++) {
                int slot = chunkIndex * LightWindow.this.sections + section;
                byte[] queuedData = data(slot) == null ? null : column.take(this.type, section);
                if (queuedData != null && queuedData != this.current[slot]) {
                    this.current[slot] = queuedData;
                    this.owned[slot] = !Nibbles.shared(queuedData);
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

            byte[] queuedData = LightWindow.this.queued.get(this.type, chunkX(chunkIndex), chunkZ(chunkIndex), section);
            byte[] data = queuedData != null ? queuedData : freshData(chunkIndex, section);
            this.current[slot] = data;
            this.owned[slot] = !Nibbles.shared(data);
            markChanged(slot);
            affectAround(chunkIndex, section);
        }

        private byte[] freshData(int chunkIndex, int section) {
            if (this.type == LightLayer.BLOCK)
                return Nibbles.ZERO;

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
