package fr.hardel.firefly.engine;

import fr.hardel.firefly.storage.ChunkLight;
import fr.hardel.firefly.storage.LightColumn;
import fr.hardel.firefly.storage.Nibbles;
import fr.hardel.firefly.storage.PendingLight;
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
    static final int[] WRITTEN = {CENTER - CHUNKS - 1, CENTER - CHUNKS, CENTER - CHUNKS + 1, CENTER - 1, CENTER, CENTER + 1, CENTER + CHUNKS - 1, CENTER + CHUNKS, CENTER + CHUNKS + 1};
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
    private final @Nullable Boolean[] initializations = new Boolean[CHUNKS * CHUNKS];
    private boolean savedApplied;
    private int chunkX;
    private int chunkZ;
    private int originX;
    private int originZ;

    LightWindow(LightChunkGetter source, LightEmission emission, ChunkSkyLightSources emptySources, boolean hasSky, int minSection, int sections) {
        this.source = source;
        this.emission = emission;
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

    boolean centerIs(LightChunk chunk) {
        return this.chunks[CENTER] == chunk;
    }

    @Nullable LightChunk center() {
        return this.chunks[CENTER];
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

        for (int chunkIndex : WRITTEN) {
            refreshColumn(chunkIndex);
        }
    }

    void initializePending(List<Propagator> propagators) {
        for (int chunkIndex : WRITTEN) {
            LightColumn column = columnIfPresent(chunkIndex);
            Boolean lighted = column == null ? null : column.initialization();
            if (lighted != null) {
                initialize(chunkIndex, lighted, propagators);
            }
        }
    }

    void startCenter() {
        LightColumn center = column(CENTER);
        PendingLight saved = center.saved();
        this.savedApplied = saved != null;
        if (saved != null) {
            this.block.apply(CENTER, saved);
            if (this.sky != null) {
                this.sky.apply(CENTER, saved);
            }
        }
    }

    static int neighborChunk(int chunkIndex, Direction direction) {
        return chunkIndex + direction.getStepX() * CHUNKS + direction.getStepZ();
    }

    int faceCell(int chunkIndex, int section, Direction direction, int along, int localY) {
        int x = direction.getStepX() == 0 ? along : direction.getStepX() > 0 ? 15 : 0;
        int z = direction.getStepZ() == 0 ? along : direction.getStepZ() > 0 ? 15 : 0;
        return (chunkIndex * this.sections + section) << SLOT_SHIFT | localY << 8 | z << 4 | x;
    }

    void unload() {
        this.block.setEnabled(CENTER, false);
        if (this.sky != null) {
            this.sky.setEnabled(CENTER, false);
        }
    }

    void publish(LightBatch batch) {
        this.block.publish(batch);
        if (this.sky != null) {
            this.sky.publish(batch);
        }

        for (int index = 0; index < this.initialized.size(); index++) {
            int chunkIndex = this.initialized.getInt(index);
            column(chunkIndex).initialized(this.initializations[chunkIndex]);
            PendingLight queued = column(chunkIndex).queued();
            if (queued != null) {
                queued.retain(false);
            }
        }

        if (this.savedApplied) {
            column(CENTER).forgetSaved();
        }
    }

    void close() {
        Arrays.fill(this.chunks, null);
        Arrays.fill(this.blockSections, null);
        this.initialized.clear();
        Arrays.fill(this.initializations, null);
        this.savedApplied = false;
        this.block.close();
        if (this.sky != null) {
            this.sky.close();
        }
    }

    private void readNonEmpty(int chunkIndex, boolean leaving) {
        for (int slot = chunkIndex * this.sections; slot < (chunkIndex + 1) * this.sections; slot++) {
            this.nonEmpty[slot] = !leaving && this.uniformStates[slot] == null;
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
        if (bottom.getLightDampening() == 0 && !Propagator.occludes(top, bottom, Direction.DOWN))
            return 0;

        this.sourceEdges[column] = edgeY;
        return 1;
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
        this.initializations[chunkIndex] = lighted;
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

    final class Layer {
        private static final byte UNCHANGED = 0;
        private static final byte ENABLED = 1;
        private static final byte DISABLED = 2;

        private final LightLayer type;
        private final byte[][] current;
        private final boolean[] owned;
        private final boolean[] removing;
        private final boolean[] changed;
        private final IntArrayList changes = new IntArrayList();
        private final List<Dequeued> dequeued = new ArrayList<>();
        private final byte[] enabling = new byte[CHUNKS * CHUNKS];

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

            Arrays.fill(this.current, null);
            Arrays.fill(this.enabling, UNCHANGED);
            this.changes.clear();
            this.dequeued.clear();
        }

        int level(int cell) {
            byte[] data = this.current[slot(cell)];
            return data == null ? -1 : Nibbles.get(data, localIndex(cell));
        }

        boolean stored(int cell) {
            return this.current[slot(cell)] != null;
        }

        boolean stored(int chunkIndex, int section) {
            return this.current[chunkIndex * LightWindow.this.sections + section] != null;
        }

        int get(int cell) {
            return Nibbles.get(this.current[slot(cell)], localIndex(cell));
        }

        void set(int cell, int level) {
            int slot = slot(cell);
            byte[] data = this.current[slot];
            if (!this.owned[slot]) {
                data = data.clone();
                this.current[slot] = data;
                this.owned[slot] = true;
                markChanged(slot);
            }

            Nibbles.set(data, localIndex(cell), level);
        }

        boolean empty(int chunkIndex, int section) {
            return this.current[chunkIndex * LightWindow.this.sections + section] == Nibbles.ZERO;
        }

        void fill(int chunkIndex, int section, byte[] data) {
            int slot = chunkIndex * LightWindow.this.sections + section;
            this.current[slot] = data;
            this.owned[slot] = false;
            markChanged(slot);
        }

        boolean enabled(int chunkIndex) {
            if (this.enabling[chunkIndex] != UNCHANGED)
                return this.enabling[chunkIndex] == ENABLED;

            LightColumn column = columnIfPresent(chunkIndex);
            return column != null && column.sections(this.type).enabled();
        }

        void setEnabled(int chunkIndex, boolean enabled) {
            this.enabling[chunkIndex] = enabled ? ENABLED : DISABLED;
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

            for (int chunkIndex : WRITTEN) {
                applyQueued(chunkIndex);
            }
        }

        private void remove(int slot) {
            int chunkIndex = slot / LightWindow.this.sections;
            int section = slot % LightWindow.this.sections;
            PendingLight queued = column(chunkIndex).queued();
            byte[] queuedData = queued == null ? null : queued.get(this.type, section);
            boolean retained = queued != null && queued.retained();
            if (retained && queuedData == null) {
                queued.put(this.type, section, this.current[slot]);
            }

            if (!retained && queuedData != null) {
                this.dequeued.add(new Dequeued(queued, slot, queuedData));
            }

            this.removing[slot] = false;
            this.current[slot] = null;
            this.owned[slot] = false;
            markChanged(slot);
        }

        private void applyQueued(int chunkIndex) {
            LightColumn column = columnIfPresent(chunkIndex);
            PendingLight queued = column == null ? null : column.queued();
            if (queued != null) {
                apply(chunkIndex, queued);
            }
        }

        private void apply(int chunkIndex, PendingLight light) {
            for (int section = 0; section < LightWindow.this.sections; section++) {
                int slot = chunkIndex * LightWindow.this.sections + section;
                byte[] lightData = this.current[slot] == null ? null : light.get(this.type, section);
                if (lightData != null) {
                    this.dequeued.add(new Dequeued(light, slot, lightData));
                }

                if (lightData != null && lightData != this.current[slot]) {
                    this.current[slot] = lightData;
                    this.owned[slot] = false;
                    markChanged(slot);
                }
            }
        }

        boolean differs(int chunkIndex, int neighborIndex, int section) {
            byte[] data = this.current[chunkIndex * LightWindow.this.sections + section];
            byte[] neighbor = this.current[neighborIndex * LightWindow.this.sections + section];
            if (data == null && neighbor == null)
                return false;

            return data != neighbor || !Nibbles.shared(data);
        }

        private void updateStored(int chunkIndex, int section, boolean stored) {
            int slot = chunkIndex * LightWindow.this.sections + section;
            boolean present = this.current[slot] != null && !this.removing[slot];
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

            LightColumn column = column(chunkIndex);
            PendingLight queued = column.queued();
            PendingLight saved = column.saved();
            byte[] queuedData = queued == null ? null : queued.get(this.type, section);
            byte[] savedData = saved == null ? null : saved.get(this.type, section);
            byte[] kept = queuedData != null ? queuedData : savedData;
            byte[] data = kept != null ? kept : freshData(chunkIndex, section, saved != null);
            this.current[slot] = data;
            this.owned[slot] = kept == null && !Nibbles.shared(data);
            markChanged(slot);
        }

        private byte[] freshData(int chunkIndex, int section, boolean lighted) {
            if (this.type == LightLayer.BLOCK || lighted && LightWindow.this.nearNonEmpty[chunkIndex * LightWindow.this.sections + section])
                return Nibbles.ZERO;

            int top = topSection(chunkIndex);
            if (top > section) {
                int above = section + 1;
                while (!stored(chunkIndex, above)) {
                    above++;
                }

                return Nibbles.bottomLayerRepeated(this.current[chunkIndex * LightWindow.this.sections + above]);
            }

            return lighted || enabled(chunkIndex) ? Nibbles.FULL : Nibbles.ZERO;
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
                int chunkIndex = slot / LightWindow.this.sections;
                int section = slot % LightWindow.this.sections;
                column(chunkIndex).sections(this.type).publish(section, this.current[slot]);
                batch.changed(this.type, SectionPos.asLong(chunkX(chunkIndex), LightWindow.this.minSection + section, chunkZ(chunkIndex)));
            }

            for (Dequeued entry : this.dequeued) {
                entry.light().remove(this.type, entry.slot() % LightWindow.this.sections, entry.data());
            }

            for (int chunkIndex : WRITTEN) {
                if (this.enabling[chunkIndex] != UNCHANGED) {
                    column(chunkIndex).sections(this.type).setEnabled(this.enabling[chunkIndex] == ENABLED);
                }
            }
        }

    }

    private record Dequeued(PendingLight light, int slot, byte[] data) {
    }
}
