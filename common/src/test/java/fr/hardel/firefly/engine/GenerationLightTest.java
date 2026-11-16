package fr.hardel.firefly.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fr.hardel.firefly.MinecraftBootstrap;
import fr.hardel.firefly.server.FireflyLightEngine;
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import sun.misc.Unsafe;

@Tag("mod")
@ExtendWith(MinecraftBootstrap.class)
class GenerationLightTest {
    private static final int CENTER = TestWorld.SIZE / 2;
    private static final ChunkPos GENERATING = new ChunkPos(CENTER, CENTER);
    private static final ChunkPos BESIDE = new ChunkPos(CENTER + 1, CENTER);
    private static final int SECTION_Y = 1;

    @Test
    void vanillaReadsAGeneratingChunkBesideAnInitializedOneAsDark() {
        TestWorld world = new TestWorld(0, -16, 64, TestWorld.PALETTE);
        LevelLightEngine vanilla = new LevelLightEngine(new Hiding(world), true, true);

        vanilla.updateSectionStatus(SectionPos.of(BESIDE, SECTION_Y), false);
        vanilla.runLightUpdates();

        assertEquals(0, vanilla.getRawBrightness(SectionPos.of(GENERATING, SECTION_Y).origin(), 0));
    }

    @Test
    void aGeneratingChunkReadsDarkUntilItIsLit() throws ReflectiveOperationException {
        TestWorld world = new TestWorld(0, -16, 64, TestWorld.PALETTE);
        Held held = new Held();
        FireflyLightEngine engine = held.create(world, true);
        BlockPos top = GENERATING.getBlockAt(0, world.maxBlockY() - 1, 0);
        ProtoChunk chunk = world.chunk(top);
        WorldGenRegion region = region(world, engine, chunk);

        assertEquals(0, region.getRawBrightness(top, 0));
        assertEquals(0, region.getBrightness(LightLayer.SKY, top));

        engine.initializeLightAhead(chunk, false);
        engine.lightChunk(chunk, false);
        held.settle(engine);

        assertEquals(15, region.getRawBrightness(top, 0));
        assertEquals(15, region.getBrightness(LightLayer.SKY, top));
    }

    private static WorldGenRegion region(TestWorld world, FireflyLightEngine engine, ChunkAccess center) throws ReflectiveOperationException {
        Unsafe unsafe = unsafe();
        ServerChunkCache chunkSource = (ServerChunkCache) unsafe.allocateInstance(ServerChunkCache.class);
        set(ServerChunkCache.class, chunkSource, "lightEngine", engine);
        ServerLevel level = (ServerLevel) unsafe.allocateInstance(ServerLevel.class);
        set(ServerLevel.class, level, "chunkSource", chunkSource);
        WorldGenRegion region = (WorldGenRegion) unsafe.allocateInstance(WorldGenRegion.class);
        set(WorldGenRegion.class, region, "level", level);
        set(WorldGenRegion.class, region, "center", center);
        set(WorldGenRegion.class, region, "generatingStep", ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES));
        set(WorldGenRegion.class, region, "cache", StaticCache2D.<GenerationChunkHolder>create(GENERATING.x(), GENERATING.z(), 1, (x, z) -> new Present(new ChunkPos(x, z), (ChunkAccess) world.getChunkForLighting(x, z))));
        return region;
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class Present extends GenerationChunkHolder {
        private final @Nullable ChunkAccess chunk;

        private Present(ChunkPos pos, @Nullable ChunkAccess chunk) {
            super(pos);
            this.chunk = chunk;
        }

        @Override
        public @Nullable ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) {
            return this.chunk;
        }

        @Override
        protected void addSaveDependency(CompletableFuture<?> future) {
        }

        @Override
        public int getTicketLevel() {
            return 0;
        }

        @Override
        public int getQueueLevel() {
            return 0;
        }
    }

    private record Hiding(LightChunkGetter world) implements LightChunkGetter {
        @Override
        public @Nullable LightChunk getChunkForLighting(int x, int z) {
            return x == GENERATING.x() && z == GENERATING.z() ? null : this.world.getChunkForLighting(x, z);
        }

        @Override
        public BlockGetter getLevel() {
            return this.world.getLevel();
        }
    }
}
