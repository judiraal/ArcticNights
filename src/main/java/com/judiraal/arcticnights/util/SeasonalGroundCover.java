package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class SeasonalGroundCover {
    private static final int SHORT_GRASS_ROLL = 96;
    private static final int TALL_GRASS_ROLL = 128;
    private static final int FOREST_LITTER_ROLL = 768;
    private static final int MAX_OCCUPIED_GRASS_NEIGHBORS = 3;
    private static final int FOREST_LITTER_MIN_SKY = 5;
    private static final int FOREST_LITTER_MAX_SKY = 13;
    private static final int CANOPY_MIN_Y_OFFSET = 5;
    private static final int CANOPY_MAX_Y_OFFSET = 10;
    private static final float MIN_REGROWTH_TEMPERATURE = ClimateService.minecraftTemperatureFromCelsius(10.0D);
    private static final int[] NEIGHBOR_Y_OFFSETS = {0, -1, 1};

    private SeasonalGroundCover() {
    }

    public static boolean canBeBuriedBySnow(BlockState state) {
        return state.is(ArcticNights.Blocks.SNOW_BURYABLE_VEGETATION);
    }

    public static void maybeRegrow(ServerLevel level, BlockPos grassPos, RandomSource random) {
        if (!ArcticNightsFeatures.climateSystem() || !ArcticNights.appliesTo(level)) return;
        if (!level.isAreaLoaded(grassPos, CANOPY_MAX_Y_OFFSET + 1)) return;
        if (!level.getBlockState(grassPos).is(ArcticNights.Blocks.SEASONAL_REGROWTH_SUBSTRATE)) return;

        BlockPos coverPos = grassPos.above();
        if (ClimateService.snowBehaviorAt(level, level.getBiome(coverPos), coverPos) != ClimateSnapshot.SnowBehavior.MELTS) return;
        if (!warmEnoughForRegrowth(level, coverPos)) return;
        if (level.getBrightness(LightLayer.BLOCK, coverPos) != 0) return;

        int skyBrightness = level.getBrightness(LightLayer.SKY, coverPos);
        if (skyBrightness - level.getSkyDarken() <= 0) return;

        boolean raining = hasRainyGrowthWeather(level, coverPos);
        BlockState coverState = level.getBlockState(coverPos);
        if (coverState.isAir()) {
            maybePlaceForestLitter(level, grassPos, coverPos, skyBrightness, raining, random);
            maybePlaceShortGrass(level, grassPos, coverPos, raining, random);
        } else if (coverState.is(Blocks.SHORT_GRASS)) {
            maybeGrowTallCover(level, grassPos, coverPos, Blocks.TALL_GRASS.defaultBlockState(), raining, random);
        } else if (coverState.is(Blocks.FERN)) {
            maybeGrowTallCover(level, grassPos, coverPos, Blocks.LARGE_FERN.defaultBlockState(), raining, random);
        }
    }

    private static void maybePlaceShortGrass(ServerLevel level, BlockPos grassPos, BlockPos coverPos, boolean raining, RandomSource random) {
        if (!level.isEmptyBlock(coverPos)) return;
        if (!roll(random, SHORT_GRASS_ROLL, raining)) return;
        if (occupiedGrassNeighborCount(level, grassPos) > MAX_OCCUPIED_GRASS_NEIGHBORS) return;

        BlockState regrowth = randomBlockFromTag(ArcticNights.Blocks.SEASONAL_SHORT_GRASS, random);
        if (!regrowth.isAir() && regrowth.canSurvive(level, coverPos)) {
            level.setBlock(coverPos, regrowth, Block.UPDATE_ALL);
        }
    }

    private static void maybeGrowTallCover(ServerLevel level, BlockPos grassPos, BlockPos coverPos, BlockState tallCover, boolean raining, RandomSource random) {
        if (!roll(random, TALL_GRASS_ROLL, raining)) return;
        if (hasNeighborCover(level, grassPos, state -> state.is(tallCover.getBlock()))) return;
        if (coverPos.getY() >= level.getMaxBuildHeight() - 1 || !level.isEmptyBlock(coverPos.above())) return;

        if (tallCover.canSurvive(level, coverPos)) {
            DoublePlantBlock.placeAt(level, tallCover, coverPos, Block.UPDATE_ALL);
        }
    }

    private static void maybePlaceForestLitter(ServerLevel level, BlockPos grassPos, BlockPos coverPos, int skyBrightness, boolean raining, RandomSource random) {
        if (!roll(random, FOREST_LITTER_ROLL, raining)) return;
        if (skyBrightness < FOREST_LITTER_MIN_SKY || skyBrightness > FOREST_LITTER_MAX_SKY) return;
        if (!hasCanopyLeaves(level, grassPos)) return;
        if (hasNeighborCover(level, grassPos, state -> state.is(ArcticNights.Blocks.SEASONAL_FOREST_LITTER))) return;

        BlockState litter = randomBlockFromTag(ArcticNights.Blocks.SEASONAL_FOREST_LITTER, random);
        if (!litter.isAir() && litter.canSurvive(level, coverPos)) {
            level.setBlock(coverPos, litter, Block.UPDATE_ALL);
        }
    }

    private static boolean roll(RandomSource random, int baseRoll, boolean raining) {
        int effectiveRoll = raining ? Math.max(1, baseRoll / 4) : baseRoll;
        return random.nextInt(effectiveRoll) == 0;
    }

    private static boolean hasRainyGrowthWeather(ServerLevel level, BlockPos pos) {
        return level.isRaining()
                && ClimateService.precipitationAtAsVanillaType(level, level.getBiome(pos), pos) == Biome.Precipitation.RAIN;
    }

    private static boolean warmEnoughForRegrowth(ServerLevel level, BlockPos pos) {
        return ClimateService.outdoorMinecraftTemperature(level, level.getBiome(pos), pos, true) > MIN_REGROWTH_TEMPERATURE;
    }

    private static int occupiedGrassNeighborCount(ServerLevel level, BlockPos grassPos) {
        int occupied = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockState cover = neighborCoverState(level, grassPos, dx, dz);
                if (cover != null && !cover.isAir()) occupied++;
            }
        }

        return occupied;
    }

    private static boolean hasNeighborCover(ServerLevel level, BlockPos grassPos, CoverPredicate predicate) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockState cover = neighborCoverState(level, grassPos, dx, dz);
                if (cover != null && predicate.test(cover)) return true;
            }
        }

        return false;
    }

    private static BlockState neighborCoverState(ServerLevel level, BlockPos grassPos, int dx, int dz) {
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int dy : NEIGHBOR_Y_OFFSETS) {
            probe.set(grassPos.getX() + dx, grassPos.getY() + dy, grassPos.getZ() + dz);
            if (level.getBlockState(probe).is(ArcticNights.Blocks.SEASONAL_REGROWTH_SUBSTRATE)) {
                return level.getBlockState(probe.above());
            }
        }

        return null;
    }

    private static boolean hasCanopyLeaves(ServerLevel level, BlockPos grassPos) {
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int dy = CANOPY_MIN_Y_OFFSET; dy <= CANOPY_MAX_Y_OFFSET; dy++) {
            probe.set(grassPos.getX(), grassPos.getY() + dy, grassPos.getZ());
            if (level.getBlockState(probe).is(BlockTags.LEAVES)) return true;
        }

        return false;
    }

    private static BlockState randomBlockFromTag(TagKey<Block> tag, RandomSource random) {
        Block selected = Blocks.AIR;
        int count = 0;
        for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
            if (random.nextInt(++count) == 0) selected = holder.value();
        }

        return selected.defaultBlockState();
    }

    private interface CoverPredicate {
        boolean test(BlockState state);
    }
}
