package com.judiraal.arcticnights.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class SnowAccumulation {
    private static final int MAX_ACCUMULATED_LAYERS = 7;
    private static final int NEIGHBOR_COUNT = 8;
    private static final int MIN_OCCUPIED_ICE_NEIGHBOR_TOPS = 2;

    private SnowAccumulation() {
    }

    public static boolean canAccumulate(LevelReader level, BlockPos pos, BlockState state) {
        if (!state.is(Blocks.SNOW)) return true;
        return state.getValue(SnowLayerBlock.LAYERS) < localLayerLimit(level, pos);
    }

    public static boolean canWeatherPlaceAt(LevelReader level, BlockPos snowPos) {
        BlockPos supportPos = snowPos.below();
        BlockState support = level.getBlockState(supportPos);
        if (support.is(Blocks.ICE)) return canWeatherPlaceOnIce(level, supportPos);
        return Blocks.SNOW.defaultBlockState().canSurvive(level, snowPos);
    }

    public static int weatherAccumulationHeight(int configuredHeight) {
        if (configuredHeight <= 0) return configuredHeight;
        return Math.max(configuredHeight, MAX_ACCUMULATED_LAYERS);
    }

    private static int localLayerLimit(LevelReader level, BlockPos pos) {
        if (!hasNeighborChunks(level, pos)) return 1;

        int neighborLayers = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                neighborLayers += neighborLayers(level, pos, dx, dz);
            }
        }

        int smoothedLimit = Math.floorDiv(neighborLayers, NEIGHBOR_COUNT);
        if (neighborLayers % NEIGHBOR_COUNT != 0 && getsDeterministicExtraLayer(pos)) {
            smoothedLimit++;
        }

        return Math.min(MAX_ACCUMULATED_LAYERS, smoothedLimit);
    }

    private static boolean getsDeterministicExtraLayer(BlockPos pos) {
        long hash = mix64(((long) pos.getX() * 0x9E3779B97F4A7C15L)
                ^ ((long) pos.getY() * 0xBF58476D1CE4E5B9L)
                ^ ((long) pos.getZ() * 0x94D049BB133111EBL));
        return (hash & 1L) == 0L;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static boolean hasNeighborChunks(LevelReader level, BlockPos pos) {
        int minChunkX = SectionPos.blockToSectionCoord(pos.getX() - 1);
        int maxChunkX = SectionPos.blockToSectionCoord(pos.getX() + 1);
        int minChunkZ = SectionPos.blockToSectionCoord(pos.getZ() - 1);
        int maxChunkZ = SectionPos.blockToSectionCoord(pos.getZ() + 1);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) return false;
            }
        }

        return true;
    }

    private static boolean canWeatherPlaceOnIce(LevelReader level, BlockPos icePos) {
        if (!hasNeighborChunks(level, icePos)) return false;

        int occupiedNeighborTops = 0;
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;

                neighbor.set(icePos.getX() + dx, icePos.getY(), icePos.getZ() + dz);
                if (level.isWaterAt(neighbor)) return false;
                if (!level.getBlockState(neighbor.above()).isAir()) {
                    occupiedNeighborTops++;
                }
            }
        }

        return occupiedNeighborTops >= MIN_OCCUPIED_ICE_NEIGHBOR_TOPS;
    }

    private static int neighborLayers(LevelReader level, BlockPos pos, int dx, int dz) {
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        probe.set(pos.getX() + dx, pos.getY(), pos.getZ() + dz);
        BlockState state = level.getBlockState(probe);
        if (state.is(Blocks.SNOW)) return state.getValue(SnowLayerBlock.LAYERS);
        if (isFullBlockNeighbor(level, probe, state)) return 8;
        return 0;
    }

    private static boolean isFullBlockNeighbor(LevelReader level, BlockPos pos, BlockState state) {
        return state.is(Blocks.SNOW_BLOCK)
                || Block.isFaceFull(state.getCollisionShape(level, pos), Direction.UP);
    }
}
