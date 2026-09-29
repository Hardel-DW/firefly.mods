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
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.shapes.Shapes;
import org.jspecify.annotations.Nullable;

final class LightWindow {
    static final int RADIUS = 2;
    static final int CHUNKS = 2 * RADIUS + 1;
    static final int CENTER = RADIUS * CHUNKS + RADIUS;
    private static final int SLOT_SHIFT = 12;
    private static final int LOCAL_MASK = (1 << SLOT_SHIFT) - 1;
    private static final BlockState ABSENT = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final int[] EDGE_MASKS = Arrays.stream(Direction.values()).mapToInt(LightWindow::edgeMask).toArray();
    private static final int[] EDGES = Arrays.stream(Direction.values()).mapToInt(LightWindow::edge).toArray();
    private static final int[] STEPS = Arrays.stream(Direction.values()).mapToInt(LightWindow::step).toArray();
    private static final int COLUMNS = 256;
    private static final int UNFOUND = Integer.MIN_VALUE;

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
    private final @Nullable LevelChunkSection[] blockSections;
    private final @Nullable BlockState[] uniformStates;
    private final int[] chunkOfSlot;
    private final int[] sectionOfSlot;
    private final int[] crossings;
    private final int[] sourceEdges = new int[COLUMNS];
    private final BlockState[] sourceTops = new BlockState[COLUMNS];
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final boolean[] nonEmpty;
    private final boolean[] nearNonEmpty;
    private final IntArrayList initialized = new IntArrayList();
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
        this.nonEmpty = new boolean[CHUNKS * CHUNKS * sections];
        this.nearNonEmpty = new boolean[CHUNKS * CHUNKS * sections];
        this.blockSections = new LevelChunkSection[CHUNKS * CHUNKS * sections];
        this.uniformStates = new BlockState[CHUNKS * CHUNKS * sections];
        this.chunkOfSlot = new int[CHUNKS * CHUNKS * sections];
        this.sectionOfSlot = new int[CHUNKS * CHUNKS * sections];
        for (int slot = 0; slot < this.chunkOfSlot.length; slot++) {
            this.chunkOfSlot[slot] = slot / sections;
            this.sectionOfSlot[slot] = slot % sections;
        }

        this.crossings = Arrays.stream(Direction.values()).mapToInt(direction -> crossing(direction, sections)).toArray();
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
                openBlocks(dx * CHUNKS + dz, chunk == null ? null : ((ChunkAccess) chunk).getSections());
            }
        }

        this.block.open();
        if (this.sky != null) {
            this.sky.open();
        }
    }

    int cell(int blockX, int blockY, int blockZ) {
        int x = blockX - this.originX;
        int y = blockY - this.minBlockY;
        int z = blockZ - this.originZ;
        int slot = ((x >> 4) * CHUNKS + (z >> 4)) * this.sections + (y >> 4);
        return slot << SLOT_SHIFT | (y & 15) << 8 | (z & 15) << 4 | x & 15;
    }

    int blockX(int cell) {
        return this.originX + (chunkIndex(cell) / CHUNKS << 4) + localX(cell);
    }

    int blockY(int cell) {
        return this.minBlockY + (sectionIndex(cell) << 4) + localY(cell);
    }

    int blockZ(int cell) {
        return this.originZ + (chunkIndex(cell) % CHUNKS << 4) + localZ(cell);
    }

    static int localX(int cell) {
        return cell & 15;
    }

    static int localY(int cell) {
        return cell >>> 8 & 15;
    }

    static int localZ(int cell) {
        return cell >>> 4 & 15;
    }

    static int slot(int cell) {
        return cell >>> SLOT_SHIFT;
    }

    static int localIndex(int cell) {
        return cell & LOCAL_MASK;
    }

    int chunkIndex(int cell) {
        return this.chunkOfSlot[slot(cell)];
    }

    int sectionIndex(int cell) {
        return this.sectionOfSlot[slot(cell)];
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
            return localY(cell) != 0 || sectionIndex(cell) != 0;

        return direction != Direction.UP || localY(cell) != 15 || sectionIndex(cell) + 1 < this.sections;
    }

    int neighbor(int cell, Direction direction) {
        int index = direction.ordinal();
        return cell + ((cell & EDGE_MASKS[index]) == EDGES[index] ? this.crossings[index] : STEPS[index]);
    }

    int withY(int cell, int sectionIndex, int localY) {
        int slot = chunkIndex(cell) * this.sections + sectionIndex;
        return slot << SLOT_SHIFT | localY << 8 | cell & 255;
    }

    BlockState state(int cell) {
        BlockState uniform = this.uniformStates[slot(cell)];
        return uniform != null ? uniform : this.blockSections[slot(cell)].getStates().get(localIndex(cell));
    }

    int emission(int cell, BlockState state) {
        if (!this.emission.mayEmit(state))
            return 0;

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
        ChunkSkyLightSources sources = sources((blockX - this.originX >> 4) * CHUNKS + (blockZ - this.originZ >> 4));
        return sources == null ? absent : sources.getLowestSourceY(SectionPos.sectionRelative(blockX), SectionPos.sectionRelative(blockZ));
    }

    void refreshStorage(boolean centerLeaving) {
        for (int chunkIndex = 0; chunkIndex < CHUNKS * CHUNKS; chunkIndex++) {
            readNonEmpty(chunkIndex, centerLeaving && chunkIndex == CENTER);
        }

        for (int slot = 0; slot < this.nonEmpty.length; slot++) {
            int section = slot % this.sections;
            this.nearNonEmpty[slot] = this.nonEmpty[slot] || section > 0 && this.nonEmpty[slot - 1] || section + 1 < this.sections && this.nonEmpty[slot + 1];
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                refreshColumn(CENTER + dx * CHUNKS + dz);
            }
        }
    }

    void initializePending(List<Propagator> propagators) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int chunkIndex = CENTER + dx * CHUNKS + dz;
                LightColumn column = columnIfPresent(chunkIndex);
                Boolean lighted = column == null ? null : column.takeInitialization();
                if (lighted != null) {
                    initialize(chunkIndex, lighted, propagators);
                }
            }
        }
    }

    void unload() {
        LightColumn center = columnIfPresent(CENTER);
        if (center != null) {
            center.sections(LightLayer.BLOCK).setEnabled(false);
            center.sections(LightLayer.SKY).setEnabled(false);
        }
    }

    void publish(LightBatch batch) {
        this.block.publish(batch);
        if (this.sky != null) {
            this.sky.publish(batch);
        }

        for (int index = 0; index < this.initialized.size(); index++) {
            int chunkIndex = this.initialized.getInt(index);
            this.queued.retain(chunkX(chunkIndex), chunkZ(chunkIndex), false);
        }
    }

    void close() {
        Arrays.fill(this.chunks, null);
        Arrays.fill(this.blockSections, null);
        this.initialized.clear();
        this.block.close();
        if (this.sky != null) {
            this.sky.close();
        }
    }

    private void readNonEmpty(int chunkIndex, boolean leaving) {
        for (int slot = chunkIndex * this.sections; slot < (chunkIndex + 1) * this.sections; slot++) {
            LevelChunkSection section = this.blockSections[slot];
            this.nonEmpty[slot] = section != null && !leaving && !section.hasOnlyAir();
        }
    }

    private void fillSources(ChunkAccess chunk, ChunkSkyLightSources sources) {
        int highestSection = chunk.getHighestFilledSectionIndex();
        if (highestSection < 0) {
            sources.fill(sources.minY);
            return;
        }

        Arrays.fill(this.sourceEdges, UNFOUND);
        Arrays.fill(this.sourceTops, AIR);
        int found = 0;
        for (int section = highestSection; section >= 0 && found < COLUMNS; section--) {
            found += findSourceEdges(chunk.getSection(section), SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(section)));
        }

        for (int column = 0; column < COLUMNS; column++) {
            sources.set(column, Math.max(this.sourceEdges[column] == UNFOUND ? sources.minY : this.sourceEdges[column], sources.minY));
        }
    }

    private int findSourceEdges(LevelChunkSection section, int sectionBottomY) {
        if (section.hasOnlyAir()) {
            Arrays.fill(this.sourceTops, AIR);
            return 0;
        }

        int found = 0;
        for (int index = COLUMNS * 16 - 1; index >= 0; index--) {
            int column = index & COLUMNS - 1;
            if (this.sourceEdges[column] == UNFOUND) {
                found += findSourceEdge(column, section.getStates().get(index), sectionBottomY + (index >> 8) + 1);
            }
        }

        return found;
    }

    private int findSourceEdge(int column, BlockState bottom, int edgeY) {
        BlockState top = this.sourceTops[column];
        this.sourceTops[column] = bottom;
        if (!edgeOccluded(top, bottom))
            return 0;

        this.sourceEdges[column] = edgeY;
        return 1;
    }

    private static boolean edgeOccluded(BlockState top, BlockState bottom) {
        if (bottom.getLightDampening() != 0)
            return true;

        if (Propagator.emptyShape(top) && Propagator.emptyShape(bottom))
            return false;

        return Shapes.faceShapeOccludes(LightEngine.getOcclusionShape(top, Direction.DOWN), LightEngine.getOcclusionShape(bottom, Direction.UP));
    }

    private void openBlocks(int chunkIndex, LevelChunkSection @Nullable [] chunkSections) {
        for (int section = 0; section < this.sections; section++) {
            int slot = chunkIndex * this.sections + section;
            boolean inside = chunkSections != null && section >= 1 && section <= chunkSections.length;
            this.blockSections[slot] = inside ? chunkSections[section - 1] : null;
            this.uniformStates[slot] = uniformState(chunkSections == null, inside ? chunkSections[section - 1] : null);
        }
    }

    private static @Nullable BlockState uniformState(boolean absent, @Nullable LevelChunkSection section) {
        if (absent)
            return ABSENT;

        return section == null || section.hasOnlyAir() ? AIR : null;
    }

    private static int edgeMask(Direction direction) {
        return 15 << switch (direction.getAxis()) {
            case X -> 0;
            case Z -> 4;
            case Y -> 8;
        };
    }

    private static int edge(Direction direction) {
        return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? edgeMask(direction) : 0;
    }

    private static int step(Direction direction) {
        return direction.getStepX() + (direction.getStepZ() << 4) + (direction.getStepY() << 8);
    }

    private static int crossing(Direction direction, int sections) {
        int slotStep = direction.getStepX() * CHUNKS * sections + direction.getStepZ() * sections + direction.getStepY();
        return (slotStep << SLOT_SHIFT) - step(direction) * 15;
    }

    private void refreshColumn(int chunkIndex) {
        if (this.chunks[chunkIndex] == null)
            return;

        for (int section = this.sections - 1; section >= 0; section--) {
            boolean stored = nearNonEmptyAround(chunkIndex, section);
            this.block.updateStored(chunkIndex, section, stored);
            if (this.sky != null) {
                this.sky.updateStored(chunkIndex, section, stored);
            }
        }
    }

    private boolean nearNonEmptyAround(int chunkIndex, int section) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (this.nearNonEmpty[(chunkIndex + dx * CHUNKS + dz) * this.sections + section]) {
                    return true;
                }
            }
        }

        return false;
    }

    private void initialize(int chunkIndex, boolean lighted, List<Propagator> propagators) {
        ChunkAccess chunk = (ChunkAccess) this.chunks[chunkIndex];
        ChunkSkyLightSources sources = chunk.getSkyLightSources();
        synchronized (sources) {
            fillSources(chunk, sources);
        }

        propagators.forEach(propagator -> propagator.setLightEnabled(chunkIndex, lighted));
        this.initialized.add(chunkIndex);
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
            return data == null ? -1 : Nibbles.get(data, localIndex(cell));
        }

        boolean stored(int cell) {
            return data(slot(cell)) != null;
        }

        boolean stored(int chunkIndex, int section) {
            return data(chunkIndex * LightWindow.this.sections + section) != null;
        }

        int get(int cell) {
            return Nibbles.get(data(slot(cell)), localIndex(cell));
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

            Nibbles.set(data, localIndex(cell), level);
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

        private void publish(LightBatch batch) {
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
                batch.changed(this.type, SectionPos.asLong(chunkX(chunkIndex), section, chunkZ(chunkIndex)));
            }
        }

    }

    private record Dequeued(int slot, byte[] data) {
    }
}
