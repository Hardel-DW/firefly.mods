package fr.hardel.firefly.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import fr.hardel.firefly.MinecraftBootstrap;
import fr.hardel.firefly.server.FireflyLightEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.common.world.AuxiliaryLightManager;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("mod")
@ExtendWith(MinecraftBootstrap.class)
class NeoForgeEmissionTest {
    private static final long SEED = 0;
    private static final int MIN_Y = -16;
    private static final int HEIGHT = 64;
    private static final int LAMPS = 40;
    private static final int ROUNDS = 3;
    private static final int LEVELS = 16;
    private static final LightOracle.Worlds WITH_POSITION_LAMPS = new LightOracle.Worlds(LightOracle.WORLDS.firstSeed(), LightOracle.WORLDS.count(),
        LightOracle.WORLDS.heights(), Stream.concat(TestWorld.PALETTE.stream(), Stream.of(lamp(TestLamps.POSITION))).toList());

    @Test
    void aBlockWhoseLightDependsOnItsPositionLightsLikeLightFromZero() {
        assertEquals(List.of(), LightOracle.differences(new Parallel(LightEmission.loaded()), WITH_POSITION_LAMPS));
    }

    @Test
    void anEmissionThatIgnoresThePositionIsCaught() {
        assertNotEquals(List.of(), LightOracle.differences(new Parallel(LightEmission.VANILLA), WITH_POSITION_LAMPS));
    }

    @Test
    void anAuxiliaryLightChangedWithoutABlockChangeLightsLikeVanilla() {
        RandomSource random = RandomSource.create(SEED);
        List<BlockPos> lamps = Stream.generate(() -> new BlockPos(random.nextInt(TestWorld.SIZE * 16), MIN_Y + HEIGHT / 2 + random.nextInt(HEIGHT / 2),
            random.nextInt(TestWorld.SIZE * 16))).limit(LAMPS).toList();
        AuxiliaryLights lights = new AuxiliaryLights();
        Parallel parallel = new Parallel(LightEmission.loaded());
        TestWorld fireflyWorld = worldWith(lamps);
        TestWorld vanillaWorld = worldWith(lamps);
        FireflyLightEngine firefly = parallel.create(new AuxiliaryWorld(fireflyWorld, lights), true);
        LevelLightEngine vanilla = LightOracle.VANILLA.create(new AuxiliaryWorld(vanillaWorld, lights), true);
        fireflyWorld.light(firefly, parallel);
        vanillaWorld.light(vanilla, LightOracle.VANILLA);

        List<String> differences = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            for (BlockPos lamp : lamps) {
                lights.setLightAt(lamp, random.nextInt(LEVELS));
                firefly.checkBlock(lamp);
                vanilla.checkBlock(lamp);
            }

            parallel.settle(firefly);
            vanilla.runLightUpdates();
            differences.addAll(LightOracle.read("round %d".formatted(round), vanilla, firefly));
        }

        assertEquals(List.of(), differences);
    }

    private static TestWorld worldWith(List<BlockPos> lamps) {
        TestWorld world = new TestWorld(SEED, MIN_Y, HEIGHT, TestWorld.PALETTE);
        lamps.forEach(pos -> world.setUnseen(new TestWorld.Edit(pos, lamp(TestLamps.AUXILIARY))));
        return world;
    }

    private static BlockState lamp(ResourceKey<Block> key) {
        return BuiltInRegistries.BLOCK.getValueOrThrow(key).defaultBlockState();
    }

    private static final class AuxiliaryLights implements AuxiliaryLightManager {
        private final Map<BlockPos, Integer> levels = new ConcurrentHashMap<>();

        @Override
        public void setLightAt(BlockPos pos, int value) {
            this.levels.put(pos.immutable(), value);
        }

        @Override
        public int getLightAt(BlockPos pos) {
            return this.levels.getOrDefault(pos, 0);
        }
    }

    private record AuxiliaryWorld(TestWorld world, AuxiliaryLightManager lights) implements LightChunkGetter, BlockGetter {
        @Override
        public @Nullable LightChunk getChunkForLighting(int x, int z) {
            return this.world.getChunkForLighting(x, z);
        }

        @Override
        public BlockGetter getLevel() {
            return this;
        }

        @Override
        public AuxiliaryLightManager getAuxLightManager(ChunkPos pos) {
            return this.lights;
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.world.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return this.world.getFluidState(pos);
        }

        @Override
        public int getHeight() {
            return this.world.getHeight();
        }

        @Override
        public int getMinY() {
            return this.world.getMinY();
        }
    }
}
