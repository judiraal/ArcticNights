package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import javax.annotation.Nullable;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.ISeasonState;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.season.SeasonHooks;

public final class ClimateService {
    private static final CachedPerTick<ServerLevel, ISeasonState> SEASON_STATE = CachedPerTick.of(SeasonHelper::getSeasonState);

    private ClimateService() {
    }

    public static ClimateSnapshot snapshot(ServerLevel level, Holder<Biome> biome, BlockPos pos) {
        return snapshot(level, biome, pos, isExposedToSky(level, pos));
    }

    public static ClimateSnapshot snapshot(ServerLevel level, Holder<Biome> biome, BlockPos pos, boolean exposedToSky) {
        float minecraftTemperature = minecraftTemperature(level, biome, pos);
        return snapshot(biome, minecraftTemperature, isRainCooling(level, biome, exposedToSky));
    }

    public static ClimateSnapshot auditSnapshot(ServerLevel level, Holder<Biome> biome, BlockPos pos, boolean exposedToSky, @Nullable Season.SubSeason subSeason, boolean raining) {
        float minecraftTemperature = auditMinecraftTemperature(level, biome, pos, exposedToSky, subSeason, raining);
        return snapshot(biome, minecraftTemperature, raining && exposedToSky && biome.value().hasPrecipitation());
    }

    private static ClimateSnapshot snapshot(Holder<Biome> biome, float minecraftTemperature, boolean rainCooling) {
        ClimateSnapshot.PrecipitationKind precipitationKind = precipitationKind(biome, minecraftTemperature);
        ClimateSnapshot.SnowBehavior snowBehavior = snowBehavior(minecraftTemperature);
        return new ClimateSnapshot(
                minecraftTemperature,
                estimatedCelsius(minecraftTemperature),
                rainCooling,
                precipitationKind,
                snowBehavior
        );
    }

    public static float minecraftTemperature(ServerLevel level, Holder<Biome> biome, BlockPos pos) {
        return baseMinecraftTemperature(level, biome, pos);
    }

    public static float spawnTemperature(ServerLevel level, Holder<Biome> biome, BlockPos pos) {
        float temperature = minecraftTemperature(level, biome, pos);
        if (isRainCooling(level, biome, pos)) return rainCooledTemperature(temperature);
        return temperature;
    }

    public static float rainCooledTemperature(float temperature) {
        return (temperature - 0.3F) * 2.0F;
    }

    public static float baseMinecraftTemperature(ServerLevel level, Holder<Biome> biome, BlockPos pos) {
        if (ArcticNights.SERENE_SEASONS) {
            return SeasonHooks.getBiomeTemperatureInSeason(SEASON_STATE.get(level.getGameTime() >> 3, level).getSubSeason(), biome, pos);
        }
        return biome.value().getTemperature(pos);
    }

    private static float auditMinecraftTemperature(ServerLevel level, Holder<Biome> biome, BlockPos pos, boolean exposedToSky, @Nullable Season.SubSeason subSeason, boolean raining) {
        return baseMinecraftTemperature(level, biome, pos, subSeason);
    }

    private static float baseMinecraftTemperature(ServerLevel level, Holder<Biome> biome, BlockPos pos, @Nullable Season.SubSeason subSeason) {
        if (ArcticNights.SERENE_SEASONS && subSeason != null) {
            return SeasonHooks.getBiomeTemperatureInSeason(subSeason, biome, pos);
        }
        return baseMinecraftTemperature(level, biome, pos);
    }

    public static boolean isRainCooling(ServerLevel level, Holder<Biome> biome, BlockPos pos) {
        return isRainCooling(level, biome, isExposedToSky(level, pos));
    }

    public static boolean isRainCooling(ServerLevel level, Holder<Biome> biome, boolean exposedToSky) {
        return level.isRaining() && exposedToSky && biome.value().hasPrecipitation();
    }

    public static boolean isExposedToSky(ServerLevel level, BlockPos pos) {
        return level.getBrightness(LightLayer.SKY, pos) != 0;
    }

    public static ClimateSnapshot.PrecipitationKind precipitationKind(Holder<Biome> biome, float minecraftTemperature) {
        if (!biome.value().hasPrecipitation()) return ClimateSnapshot.PrecipitationKind.NONE;
        return minecraftTemperature < 0.15F ? ClimateSnapshot.PrecipitationKind.SNOW : ClimateSnapshot.PrecipitationKind.RAIN;
    }

    public static ClimateSnapshot.SnowBehavior snowBehavior(float minecraftTemperature) {
        if (minecraftTemperature < -0.15F) return ClimateSnapshot.SnowBehavior.PERSISTENT;
        if (minecraftTemperature < 0.15F) return ClimateSnapshot.SnowBehavior.TRANSITIONAL_SURFACE;
        return ClimateSnapshot.SnowBehavior.MELTS;
    }

    public static double estimatedCelsius(float minecraftTemperature) {
        return Math.round((((double) minecraftTemperature - 0.15D) * 40.0D - 3.0D) * 10.0D) / 10.0D;
    }

}
