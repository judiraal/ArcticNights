package com.judiraal.arcticnights.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.Tags;
import sereneseasons.config.SeasonsConfig;
import sereneseasons.init.ModTags;
import sereneseasons.season.SeasonHooks;

public final class SnowMeltHandler {
    private SnowMeltHandler() {}

    public static void meltInChunk(LevelChunk chunkIn, float meltChance, SeasonsConfig.SeasonProperties seasonProperties) {
        final ServerLevel level = (ServerLevel) chunkIn.getLevel();

        if (meltChance <= 0.0F || level.random.nextFloat() >= meltChance) return;

        final ChunkPos chunkPos = chunkIn.getPos();
        final int minX = chunkPos.getMinBlockX();
        final int minZ = chunkPos.getMinBlockZ();

        BlockPos.MutableBlockPos surfacePos = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING,
                level.getBlockRandomPos(minX, 0, minZ, 15)
        ).mutable();

        BlockState surfaceState = level.getBlockState(surfacePos);
        if (!surfaceState.is(Blocks.SNOW)) {
            surfacePos.move(Direction.DOWN);
            surfaceState = level.getBlockState(surfacePos);
        }

        final Holder<Biome> biomeHolder = level.getBiome(surfacePos);
        if (biomeHolder.is(ModTags.Biomes.BLACKLISTED_BIOMES) || biomeHolder.is(Tags.Biomes.IS_CAVE)) return;

        final float temp = SeasonHooks.getBiomeTemperatureInSeason(seasonProperties.subSeason(), biomeHolder, surfacePos);
        if (temp < -0.15F) return;
        if (temp < 0.15F) {
            final BlockPos orgPos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, surfacePos);
            if (surfacePos.getY() <= orgPos.getY()) return;
        }

        doMelt(surfaceState, level, surfacePos);

        tryMeltDown(level, surfacePos, 1);
    }

    private static void tryMeltDown(ServerLevel level, BlockPos.MutableBlockPos pos, int attempt) {
        if (level.random.nextFloat() < 0.3F) {
            int yTopExclusive = pos.getY();
            int down = level.random.nextInt(attempt*8) + 1;
            pos.move(Direction.DOWN, down);
            if (!level.isLoaded(pos)) return;
            if (findFirstTopSnow(level, pos, yTopExclusive) && level.getBrightness(LightLayer.SKY, pos.above()) > 0)
                doMelt(level.getBlockState(pos), level, pos);
            else
                tryMeltDown(level, pos.setY(yTopExclusive - down), attempt + 1);
        }
    }

    private static boolean isSnow(BlockState blockState) {
        return blockState.is(Blocks.SNOW) || blockState.is(Blocks.SNOW_BLOCK);
    }

    private static boolean findFirstTopSnow(Level level, BlockPos.MutableBlockPos pos, int yTop) {
        while (pos.getY() < yTop && !isSnow(level.getBlockState(pos)))
            pos = pos.move(Direction.UP);
        if (pos.getY() == yTop) return false;
        pos.move(Direction.UP);
        while (isSnow(level.getBlockState(pos)))
            pos = pos.move(Direction.UP);
        pos.move(Direction.DOWN);
        return true;
    }

    private static void doMelt(BlockState state, ServerLevel level, BlockPos pos) {
        if (state.is(Blocks.SNOW)) {
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers <= 1) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            } else {
                level.setBlockAndUpdate(pos, state.setValue(SnowLayerBlock.LAYERS, layers - 1));
            }
        } else if (state.is(Blocks.SNOW_BLOCK)) {
            BlockState newState = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 7);
            level.setBlockAndUpdate(pos, newState);
        } else if (state.is(Blocks.ICE)) {
            ((IceBlock) Blocks.ICE).melt(state, level, pos);
        }
    }
}
