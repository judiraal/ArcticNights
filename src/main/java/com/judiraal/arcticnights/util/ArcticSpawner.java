package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsConfig;
import it.unimi.dsi.fastutil.longs.Long2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.biome.Biome;
import sereneseasons.api.season.ISeasonState;
import sereneseasons.api.season.SeasonHelper;

import javax.annotation.Nullable;

public class ArcticSpawner {
    private static final int LAVA_LAKE_HORIZONTAL_RADIUS = 9;
    private static final int LAVA_LAKE_HORIZONTAL_STEP = 3;
    private static final int LAVA_LAKE_VERTICAL_RADIUS = 4;
    private static final int LAVA_LAKE_VERTICAL_STEP = 2;
    private static final int LAVA_LAKE_MIN_SAMPLED_SOURCES = 8;
    private static final Long2FloatLinkedOpenHashMap TEMPERATURE_CACHE = new Long2FloatLinkedOpenHashMap(1024, 0.25F) {
        @Override
        protected void rehash(int newSize) {
        }
    };
    static {
        TEMPERATURE_CACHE.defaultReturnValue(Float.NaN);
    }
    private static final Long2ByteLinkedOpenHashMap LAVA_LAKE_CACHE = new Long2ByteLinkedOpenHashMap(1024, 0.25F) {
        @Override
        protected void rehash(int newSize) {
        }
    };
    static {
        LAVA_LAKE_CACHE.defaultReturnValue((byte) 0);
    }
    private static long lastClearedGameTime;
    private static long lastClearedLavaGameTime;

    public static float getTemperature(ServerLevel level, @Nullable Holder<Biome> biome, BlockPos pos) {
        if (level.getGameTime() >> 11 != lastClearedGameTime) {
            lastClearedGameTime = level.getGameTime() >> 11;
            TEMPERATURE_CACHE.clear();
        }
        var p = pos.asLong(pos.getX() >> 2, pos.getY() >> 1, pos.getZ() >> 2) + ((level.getGameTime() >> 3) & 511);
        float t = TEMPERATURE_CACHE.get(p);
        if (!Float.isNaN(t)) return t;
        if (TEMPERATURE_CACHE.size() == 1024) TEMPERATURE_CACHE.removeFirstFloat();
        if (biome == null) biome = level.getBiome(pos);
        t = ClimateService.spawnTemperature(level, biome, pos);
        TEMPERATURE_CACHE.put(p, t);
        return t;
    }

    private static boolean isRainCooling(ServerLevel level, Holder<Biome> biome, BlockPos pos) {
        return ClimateService.isRainCooling(level, biome, pos);
    }

    public static float spawnFactor(EntityType<?> entityType, BlockPos pos, ServerLevel level) {
        if (entityType == EntityType.WITCH) {
            var biome = level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
            return biome.is(SpawnEcology.WITCH_WETLANDS) ? 1.0F : 0.0F;
        }
        if (entityType.is(SpawnEcology.REQUIRE_COLD)) {
            var biome = level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
            boolean exposedToSky = ClimateService.isExposedToSky(level, pos);
            ClimateSnapshot.WeatherState weatherState = ClimateService.weatherStateAt(level, biome, exposedToSky);
            var temp = getTemperature(level, biome, pos);
            float dryTemperature = temp;
            boolean rainCooling = false;
            if (weatherState != ClimateSnapshot.WeatherState.CLEAR && biome.value().hasPrecipitation()) {
                rainCooling = true;
                dryTemperature = ClimateService.clearOutdoorMinecraftTemperature(level, biome, pos, exposedToSky);
            }
            float factor = SpawnEcology.undeadFactor(biome, temp, dryTemperature, getCaveFactor(level, pos), rainCooling);
            return SpawnEcology.undeadSurfaceWeatherFactor(
                    factor,
                    isSnowOrIceSurface(level.getBlockState(pos.below())),
                    isActivelySnowing(biome, weatherState, temp)
            );
        } else if (entityType.is(SpawnEcology.REQUIRE_AUTUMN_OR_DEEP)) {
            var caveFactor = getCaveFactor(level, pos);
            var deepFactor = caveFactor >= 0.5F ? 1.0F : 0.0F;
            if (deepFactor > 0.0F) return deepFactor;
            var biome = level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
            boolean exposedToSky = ClimateService.isExposedToSky(level, pos);
            ClimateSnapshot.WeatherState weatherState = ClimateService.weatherStateAt(level, biome, exposedToSky);
            float temperature = ClimateService.outdoorMinecraftTemperature(level, biome, pos, exposedToSky, weatherState);
            return SpawnEcology.spiderFactor(
                    autumnProgressionFactor(level),
                    temperature,
                    weatherState != ClimateSnapshot.WeatherState.CLEAR,
                    weatherState,
                    caveFactor
            );
        } else if (entityType.is(SpawnEcology.REQUIRE_HOT)) {
            if (entityType == EntityType.CREEPER) return creeperSpawnProbe(pos, level).factor();
            var biome = level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
            return SpawnEcology.creeperFactor(biome, ClimateService.outdoorMinecraftTemperature(level, biome, pos), getCaveFactor(level, pos));
        }
        return 1.0F;
    }

    public static CreeperSpawnProbe creeperSpawnProbe(BlockPos pos, ServerLevel level) {
        var biome = level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
        ResourceLocation biomeId = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome.value());
        float temperature = ClimateService.outdoorMinecraftTemperature(level, biome, pos);
        float caveFactor = getCaveFactor(level, pos);
        boolean nearUndergroundLavaLake = isNearUndergroundLavaLake(level, pos);
        float factor = nearUndergroundLavaLake
                ? 1.0F
                : SpawnEcology.creeperFactor(biome, temperature, caveFactor);
        return new CreeperSpawnProbe(
                biomeId,
                ClimateService.estimatedCelsius(temperature),
                temperature,
                caveFactor,
                nearUndergroundLavaLake,
                factor
        );
    }

    public record CreeperSpawnProbe(
            @Nullable ResourceLocation biomeId,
            double estimatedCelsius,
            float minecraftTemperature,
            float caveFactor,
            boolean nearUndergroundLavaLake,
            float factor
    ) {
    }

    public static boolean isUndeadColdSpawnSurface(BlockState state, EntityType<?> entityType) {
        return entityType.is(SpawnEcology.REQUIRE_COLD) && (isIceSurface(state) || isLayeredSnow(state));
    }

    private static boolean isLayeredSnow(BlockState state) {
        return state.is(Blocks.SNOW)
                && state.hasProperty(SnowLayerBlock.LAYERS);
    }

    private static boolean isActivelySnowing(Holder<Biome> biome, ClimateSnapshot.WeatherState weatherState, float outdoorMinecraftTemperature) {
        return weatherState != ClimateSnapshot.WeatherState.CLEAR
                && ClimateService.precipitationKind(biome, outdoorMinecraftTemperature) == ClimateSnapshot.PrecipitationKind.SNOW;
    }

    private static boolean isSnowOrIceSurface(BlockState state) {
        return state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.POWDER_SNOW)
                || isIceSurface(state);
    }

    private static boolean isIceSurface(BlockState state) {
        return state.is(Blocks.ICE)
                || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.FROSTED_ICE);
    }

    private static float autumnProgressionFactor(ServerLevel level) {
        float day = ArcticNights.SERENE_SEASONS ? SeasonsCompat.getYearDay(level) : fallbackYearDay(level);
        return SpawnEcology.autumnProgressionFactor(day);
    }

    private static float fallbackYearDay(ServerLevel level) {
        int daysPerYear = ArcticNightsConfig.daysPerSeason.get() << 2;
        if (daysPerYear <= 0) return 32.0F;
        long day = level.getDayTime() / 24_000L;
        return Math.floorMod(day * 96L / daysPerYear, 96L);
    }

    private static float getCaveFactor(ServerLevel level, BlockPos pos) {
        if (pos.getY() > 64) return 0;
        if (pos.getY() < 32) return 0.8F;
        return (float)(15-level.getBrightness(LightLayer.SKY, pos))/15F*0.8F;
    }

    private static boolean isNearUndergroundLavaLake(ServerLevel level, BlockPos pos) {
        if (pos.getY() >= 48) return false;
        if (level.getGameTime() >> 8 != lastClearedLavaGameTime) {
            lastClearedLavaGameTime = level.getGameTime() >> 8;
            LAVA_LAKE_CACHE.clear();
        }
        long key = BlockPos.asLong(pos.getX() >> 3, pos.getY() >> 3, pos.getZ() >> 3);
        byte cached = LAVA_LAKE_CACHE.get(key);
        if (cached != 0) return cached == 1;
        int lavaSources = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int currentChunkX = Integer.MIN_VALUE;
        int currentChunkZ = Integer.MIN_VALUE;
        LevelChunk currentChunk = null;
        for (int x = pos.getX() - LAVA_LAKE_HORIZONTAL_RADIUS; x <= pos.getX() + LAVA_LAKE_HORIZONTAL_RADIUS; x += LAVA_LAKE_HORIZONTAL_STEP) {
            int chunkX = x >> 4;
            for (int z = pos.getZ() - LAVA_LAKE_HORIZONTAL_RADIUS; z <= pos.getZ() + LAVA_LAKE_HORIZONTAL_RADIUS; z += LAVA_LAKE_HORIZONTAL_STEP) {
                int chunkZ = z >> 4;
                if (chunkX != currentChunkX || chunkZ != currentChunkZ) {
                    currentChunkX = chunkX;
                    currentChunkZ = chunkZ;
                    currentChunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                }
                if (currentChunk == null) continue;
                int minY = Math.max(level.getMinBuildHeight(), pos.getY() - LAVA_LAKE_VERTICAL_RADIUS);
                int maxY = Math.min(level.getMaxBuildHeight() - 1, pos.getY() + LAVA_LAKE_VERTICAL_RADIUS);
                for (int y = minY; y <= maxY; y += LAVA_LAKE_VERTICAL_STEP) {
                    cursor.set(x, y, z);
                    var fluid = currentChunk.getFluidState(cursor);
                    if (fluid.is(Fluids.LAVA) && fluid.isSource() && ++lavaSources >= LAVA_LAKE_MIN_SAMPLED_SOURCES) {
                        cacheLavaLakeResult(key, true);
                        return true;
                    }
                }
            }
        }
        cacheLavaLakeResult(key, false);
        return false;
    }

    private static void cacheLavaLakeResult(long key, boolean result) {
        if (LAVA_LAKE_CACHE.size() == 1024) LAVA_LAKE_CACHE.removeFirstByte();
        LAVA_LAKE_CACHE.put(key, result ? (byte) 1 : (byte) 2);
    }

    private static class SeasonsCompat {
        private static final CachedPerTick<Level, ISeasonState> SEASON_STATE = CachedPerTick.of(SeasonHelper::getSeasonState);

        static ISeasonState getSeason(ServerLevel level) {
            return SEASON_STATE.get(level.getGameTime() >> 3, level);
        }

        static float getYearDay(ServerLevel level) {
            return getSeason(level).getDay();
        }
    }
}
