package fr.hardel.firefly.engine;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.ProtoChunkTicks;
import org.jspecify.annotations.Nullable;

final class TestWorld implements LightChunkGetter, BlockGetter {
    static final int SIZE = 7;
    static final List<BlockState> PALETTE = List.of(
        Blocks.AIR.defaultBlockState(),
        Blocks.STONE.defaultBlockState(),
        Blocks.GLOWSTONE.defaultBlockState(),
        Blocks.SEA_LANTERN.defaultBlockState(),
        Blocks.MAGMA_BLOCK.defaultBlockState(),
        Blocks.GLASS.defaultBlockState(),
        Blocks.TINTED_GLASS.defaultBlockState(),
        Blocks.WATER.defaultBlockState(),
        Blocks.COBWEB.defaultBlockState(),
        Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
        Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
        Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
        Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.TOP));
    private static final PalettedContainerFactory CONTAINERS = containers();

    private final LevelHeightAccessor height;
    private final List<BlockState> palette;
    private final ProtoChunk[] chunks = new ProtoChunk[SIZE * SIZE];
    private @Nullable LightLog log;

    TestWorld(long seed, int minY, int height, List<BlockState> palette) {
        this.height = LevelHeightAccessor.create(minY, height);
        this.palette = palette;
        RandomSource random = RandomSource.create(seed);
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                this.chunks[x * SIZE + z] = new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, this.height, CONTAINERS, null);
            }
        }

        terrain(random);
        caves(random);
        platforms(random);
        scatter(random, SIZE * SIZE * 24);
    }

    <E extends LevelLightEngine> void light(E engine, LightOracle.Engine<E> tested) {
        for (ProtoChunk chunk : this.chunks) {
            chunk.initializeLightSources();
            for (int index = 0; index < chunk.getSectionsCount(); index++) {
                if (!chunk.getSection(index).hasOnlyAir()) {
                    engine.updateSectionStatus(SectionPos.of(chunk.getPos(), chunk.getSectionYFromSectionIndex(index)), false);
                }
            }
        }

        tested.settle(engine);
        for (ProtoChunk chunk : this.chunks) {
            engine.setLightEnabled(chunk.getPos(), false);
            engine.propagateLightSources(chunk.getPos());
            chunk.setPersistedStatus(ChunkStatus.LIGHT);
            chunk.setLightEngine(engine);
        }

        tested.settle(engine);
    }

    void watch(LightLog log) {
        this.log = log;
    }

    void set(Edit edit) {
        chunk(edit.pos).setBlockState(edit.pos, edit.state, Block.UPDATE_NONE);
    }

    void setUnseen(Edit edit) {
        section(edit.pos).setBlockState(edit.pos.getX() & 15, edit.pos.getY() & 15, edit.pos.getZ() & 15, edit.state);
    }

    List<ProtoChunk> chunks() {
        return List.of(this.chunks);
    }

    int minBlockY() {
        return this.height.getMinY();
    }

    int maxBlockY() {
        return this.height.getMaxY();
    }

    @Override
    public @Nullable LightChunk getChunkForLighting(int x, int z) {
        return x < 0 || z < 0 || x >= SIZE || z >= SIZE ? null : this.chunks[x * SIZE + z];
    }

    @Override
    public void onLightUpdate(LightLayer layer, SectionPos pos) {
        if (this.log != null) {
            this.log.notified(layer, pos);
        }
    }

    @Override
    public BlockGetter getLevel() {
        return this;
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        LightChunk chunk = getChunkForLighting(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public int getHeight() {
        return this.height.getHeight();
    }

    @Override
    public int getMinY() {
        return this.height.getMinY();
    }

    ProtoChunk unload(ChunkPos pos) {
        ProtoChunk unloaded = this.chunks[pos.x() * SIZE + pos.z()];
        this.chunks[pos.x() * SIZE + pos.z()] = null;
        return unloaded;
    }

    ProtoChunk load(ProtoChunk unloaded, ChunkStatus status, boolean lightCorrect) {
        ChunkPos pos = unloaded.getPos();
        ProtoChunk loaded = new ProtoChunk(pos, UpgradeData.EMPTY, unloaded.getSections(), new ProtoChunkTicks<>(), new ProtoChunkTicks<>(), this.height, CONTAINERS, null);
        loaded.setPersistedStatus(status);
        loaded.setLightCorrect(lightCorrect);
        this.chunks[pos.x() * SIZE + pos.z()] = loaded;
        return loaded;
    }

    ProtoChunk chunk(BlockPos pos) {
        return this.chunks[SectionPos.blockToSectionCoord(pos.getX()) * SIZE + SectionPos.blockToSectionCoord(pos.getZ())];
    }

    private LevelChunkSection section(BlockPos pos) {
        ProtoChunk chunk = chunk(pos);
        return chunk.getSection(chunk.getSectionIndex(pos.getY()));
    }

    private void terrain(RandomSource random) {
        double phaseX = random.nextDouble() * Math.PI * 2;
        double phaseZ = random.nextDouble() * Math.PI * 2;
        double amplitude = getHeight() / 6.0;
        int base = getMinY() + getHeight() / 3;
        int sea = base + 2;
        for (int x = 0; x < SIZE * 16; x++) {
            for (int z = 0; z < SIZE * 16; z++) {
                int ground = base + (int) (amplitude * (Math.sin(x / 9.0 + phaseX) + Math.cos(z / 11.0 + phaseZ)) / 2);
                for (int y = getMinY(); y <= Math.max(ground, sea); y++) {
                    setUnseen(new Edit(new BlockPos(x, y, z), y <= ground ? Blocks.STONE.defaultBlockState() : Blocks.WATER.defaultBlockState()));
                }
            }
        }
    }

    private void caves(RandomSource random) {
        for (int cave = 0; cave < SIZE * SIZE / 2; cave++) {
            BlockPos center = new BlockPos(random.nextInt(SIZE * 16), getMinY() + random.nextInt(getHeight() / 2), random.nextInt(SIZE * 16));
            int radius = 2 + random.nextInt(4);
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
                if (pos.distSqr(center) <= radius * radius && inside(pos)) {
                    setUnseen(new Edit(pos, Blocks.AIR.defaultBlockState()));
                }
            }
        }
    }

    private void platforms(RandomSource random) {
        for (int platform = 0; platform < SIZE; platform++) {
            int x = random.nextInt(SIZE * 16 - 12);
            int z = random.nextInt(SIZE * 16 - 12);
            int y = getMaxY() - 1 - random.nextInt(Math.max(1, getHeight() / 4));
            for (BlockPos pos : BlockPos.betweenClosed(x, y, z, x + 3 + random.nextInt(9), y, z + 3 + random.nextInt(9))) {
                setUnseen(new Edit(pos, Blocks.STONE.defaultBlockState()));
            }
        }
    }

    private void scatter(RandomSource random, int count) {
        for (int block = 0; block < count; block++) {
            setUnseen(randomEdit(random, this.height, this.palette));
        }
    }

    static Edit randomEdit(RandomSource random, LevelHeightAccessor height, List<BlockState> palette) {
        BlockPos pos = new BlockPos(random.nextInt(SIZE * 16), height.getMinY() + random.nextInt(height.getHeight()), random.nextInt(SIZE * 16));
        return new Edit(pos, palette.get(random.nextInt(palette.size())));
    }

    private boolean inside(BlockPos pos) {
        return pos.getX() >= 0 && pos.getZ() >= 0 && pos.getX() < SIZE * 16 && pos.getZ() < SIZE * 16 && !this.height.isOutsideBuildHeight(pos);
    }

    record Edit(BlockPos pos, BlockState state) {
    }

    private static PalettedContainerFactory containers() {
        Holder<Biome> plains = VanillaRegistries.createLookup().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        CrudeIncrementalIntIdentityHashBiMap<Holder<Biome>> biomes = CrudeIncrementalIntIdentityHashBiMap.create(1);
        biomes.add(plains);
        Strategy<BlockState> blockStrategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        Strategy<Holder<Biome>> biomeStrategy = Strategy.createForBiomes(biomes);
        BlockState air = Blocks.AIR.defaultBlockState();
        return new PalettedContainerFactory(blockStrategy, air, PalettedContainer.codecRW(BlockState.CODEC, blockStrategy, air), biomeStrategy, plains,
            PalettedContainer.codecRO(Biome.CODEC, biomeStrategy, plains));
    }
}
