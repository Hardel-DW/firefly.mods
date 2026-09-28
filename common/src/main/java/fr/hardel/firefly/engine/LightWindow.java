package fr.hardel.firefly.engine;

import fr.hardel.firefly.storage.ChunkLight;
import fr.hardel.firefly.storage.LightColumn;
import fr.hardel.firefly.storage.Nibbles;
import fr.hardel.firefly.storage.QueuedSections;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    private final int minSection;
    private final int sections;
    private final int minBlockY;
    private final @Nullable LightChunk[] chunks = new LightChunk[CHUNKS * CHUNKS];
    private final LevelChunkSection[][] blocks = new LevelChunkSection[CHUNKS * CHUNKS][];
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private int chunkX;
    private int chunkZ;
    private int originX;
    private int originZ;

    LightWindow(LightChunkGetter source, LightEmission emission, QueuedSections queued, ChunkSkyLightSources emptySources, boolean hasSky, int minSection, int sections) {
        this.source = source;
        this.emission = emission;
        this.queued = queued;
        this.emptySources = emptySources;
        this.minSection = minSection;
        this.sections = sections;
        this.minBlockY = SectionPos.sectionToBlockCoord(minSection);
        this.block = new Layer(LightLayer.BLOCK);
        this.sky = hasSky ? new Layer(LightLayer.SKY) : null;
    }

    void open(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.originX = SectionPos.sectionToBlockCoord(chunkX - RADIUS);
        this.originZ = SectionPos.sectionToBlockCoord(chunkZ - RADIUS);
        for (int dx = 0; dx < CHUNKS; dx++) {
            for (int dz = 0; dz < CHUNKS; dz++) {
                LightChunk chunk = this.source.getChunkForLighting(chunkX - RADIUS + dx, chunkZ - RADIUS + dz);
                this.chunks[dx * CHUNKS + dz] = chunk;
                this.blocks[dx * CHUNKS + dz] = chunk == null ? null : ((ChunkAccess) chunk).getSections();
            }
        }

        this.block.open();
        if (this.sky != null) {
            this.sky.open();
        }
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

    void initialize() {
        ChunkAccess center = (ChunkAccess) center();
        ChunkSkyLightSources sources = center.getSkyLightSources();
        synchronized (sources) {
            center.initializeLightSources();
        }

        LevelChunkSection[] sections = this.blocks[CENTER];
        for (int index = 0; index < sections.length; index++) {
            if (!sections[index].hasOnlyAir()) {
                sectionStatus(index + 1, false);
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

    void close() {
        Arrays.fill(this.chunks, null);
        Arrays.fill(this.blocks, null);
        this.block.close();
        if (this.sky != null) {
            this.sky.close();
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

    private static int below(int local) {
        return local == 0 ? -1 : 0;
    }

    private static int above(int local) {
        return local == 15 ? 1 : 0;
    }

    final class Layer {
        private final LightLayer type;
        private final byte[][] current;
        private final boolean[] owned;
        private final boolean[] removing;
        private final boolean[] changed;
        private final int notifiedSections = LightWindow.this.sections + 2;
        private final boolean[] notified = new boolean[CHUNKS * CHUNKS * this.notifiedSections];
        private final IntArrayList changes = new IntArrayList();
        private final IntArrayList notifications = new IntArrayList();
        private final List<Dequeued> dequeued = new ArrayList<>();

        private Layer(LightLayer type) {
            int slots = CHUNKS * CHUNKS * LightWindow.this.sections;
            this.type = type;
            this.current = new byte[slots][];
            this.owned = new boolean[slots];
            this.removing = new boolean[slots];
            this.changed = new boolean[slots];
        }

        private void open() {
            for (int chunkIndex = 0; chunkIndex < CHUNKS * CHUNKS; chunkIndex++) {
                LightColumn column = columnIfPresent(chunkIndex);
                for (int section = 0; column != null && section < LightWindow.this.sections; section++) {
                    this.current[chunkIndex * LightWindow.this.sections + section] = column.sections(this.type).get(section);
                }
            }
        }

        private void close() {
            for (int index = 0; index < this.changes.size(); index++) {
                int slot = this.changes.getInt(index);
                this.owned[slot] = false;
                this.changed[slot] = false;
            }

            for (int index = 0; index < this.notifications.size(); index++) {
                this.notified[this.notifications.getInt(index)] = false;
            }

            Arrays.fill(this.current, null);
            this.changes.clear();
            this.notifications.clear();
            this.dequeued.clear();
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
            int chunkIndex = chunkIndex(cell);
            int section = sectionIndex(cell);
            for (int dx = below(x); dx <= above(x); dx++) {
                for (int dz = below(z); dz <= above(z); dz++) {
                    for (int dy = below(y); dy <= above(y); dy++) {
                        notifyLater(chunkIndex + dx * CHUNKS + dz, section + dy);
                    }
                }
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
            byte[] queuedData = column == null ? null : column.get(this.type, section);
            boolean retained = column != null && column.retained();
            if (retained && queuedData == null) {
                column.put(this.type, section, this.current[slot]);
            }

            if (!retained && queuedData != null) {
                this.dequeued.add(new Dequeued(slot, queuedData));
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
                byte[] queuedData = data(slot) == null ? null : column.get(this.type, section);
                if (queuedData != null) {
                    this.dequeued.add(new Dequeued(slot, queuedData));
                }

                if (queuedData != null && queuedData != this.current[slot]) {
                    this.current[slot] = queuedData;
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

            byte[] queuedData = LightWindow.this.queued.get(this.type, chunkX(chunkIndex), chunkZ(chunkIndex), section);
            byte[] data = queuedData != null ? queuedData : freshData(chunkIndex, section);
            this.current[slot] = data;
            this.owned[slot] = queuedData == null && !Nibbles.shared(data);
            markChanged(slot);
            notifyLater(chunkIndex, section);
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

        private void notifyLater(int chunkIndex, int section) {
            int index = chunkIndex * this.notifiedSections + section + 1;
            if (!this.notified[index]) {
                this.notified[index] = true;
                this.notifications.add(index);
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

            for (Dequeued entry : this.dequeued) {
                int chunkIndex = entry.slot() / LightWindow.this.sections;
                QueuedSections.Column column = LightWindow.this.queued.column(chunkX(chunkIndex), chunkZ(chunkIndex));
                if (column != null) {
                    column.remove(this.type, entry.slot() % LightWindow.this.sections, entry.data());
                }
            }

            for (int index = 0; index < this.notifications.size(); index++) {
                int notification = this.notifications.getInt(index);
                int chunkIndex = notification / this.notifiedSections;
                int section = LightWindow.this.minSection + notification % this.notifiedSections - 1;
                LightWindow.this.source.onLightUpdate(this.type, SectionPos.of(chunkX(chunkIndex), section, chunkZ(chunkIndex)));
            }
        }

        private int slot(int cell) {
            return chunkIndex(cell) * LightWindow.this.sections + sectionIndex(cell);
        }
    }

    private record Dequeued(int slot, byte[] data) {
    }
}
