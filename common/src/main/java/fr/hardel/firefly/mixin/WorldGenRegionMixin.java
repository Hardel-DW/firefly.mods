package fr.hardel.firefly.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionMixin implements WorldGenLevel {
    @Shadow
    public abstract ChunkAccess getChunk(int chunkX, int chunkZ);

    @Shadow
    public abstract boolean hasChunk(int chunkX, int chunkZ);

    @Shadow
    public abstract LevelLightEngine getLightEngine();

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return firefly$unlit(pos) ? 0 : getLightEngine().getLayerListener(layer).getLightValue(pos);
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return firefly$unlit(pos) ? 0 : getLightEngine().getRawBrightness(pos, darkening);
    }

    @Unique
    private boolean firefly$unlit(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        return hasChunk(chunkX, chunkZ) && !getChunk(chunkX, chunkZ).isLightCorrect();
    }
}
