package com.judiraal.arcticnights.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public final class UnderTreeSnow {
    private UnderTreeSnow() {
    }

    public static boolean tryPlace(ServerLevel level, BlockPos precipitationSample) {
        boolean historicalClimate = ClimateTickrateCatchup.isReplayingHistoricalClimate(level);
        boolean historicalPrecipitation = ClimateTickrateCatchup.isReplayingHistoricalPrecipitation(level);
        if (historicalClimate) {
            if (!historicalPrecipitation) return false;
        } else if (!level.isRaining()) {
            return false;
        }

        BlockPos canopySurface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, precipitationSample);
        if (!level.getBlockState(canopySurface.below()).is(BlockTags.LEAVES)) return false;

        BlockPos snowPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, precipitationSample);
        var biome = level.getBiome(snowPos);
        if (historicalPrecipitation) {
            float outdoorTemperature = ClimateTickrateCatchup.historicalOutdoorMinecraftTemperature(level, biome, snowPos, true);
            long dayTime = ClimateTickrateCatchup.historicalReplayDayTime(level);
            if (!ClimateService.shouldSnow(level, biome, snowPos, outdoorTemperature, dayTime)) return false;
        } else if (!ClimateService.shouldSnow(level, biome, snowPos)) {
            return false;
        }

        int configuredHeight = level.getGameRules().getInt(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT);
        int maxLayers = SnowAccumulation.weatherAccumulationHeight(configuredHeight);
        if (maxLayers <= 0) return false;

        BlockState currentState = level.getBlockState(snowPos);
        if (currentState.is(Blocks.SNOW)) {
            return addLayer(level, snowPos, currentState, maxLayers);
        }

        return placeSnow(level, snowPos, Blocks.SNOW.defaultBlockState());
    }

    private static boolean addLayer(ServerLevel level, BlockPos pos, BlockState currentState, int maxLayers) {
        int layers = currentState.getValue(SnowLayerBlock.LAYERS);
        if (layers >= Math.min(maxLayers, 8)) return false;

        BlockState nextState = currentState.setValue(SnowLayerBlock.LAYERS, layers + 1);
        Block.pushEntitiesUp(currentState, nextState, level, pos);
        return level.setBlockAndUpdate(pos, nextState);
    }

    private static boolean placeSnow(ServerLevel level, BlockPos pos, BlockState snowState) {
        if (SeasonalGroundCover.canBeBuriedBySnow(level.getBlockState(pos))) {
            return level.setBlock(pos, snowState, Block.UPDATE_ALL);
        }
        return level.setBlockAndUpdate(pos, snowState);
    }
}
