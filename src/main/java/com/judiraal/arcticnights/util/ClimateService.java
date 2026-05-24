package com.judiraal.arcticnights.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import javax.annotation.Nullable;

public final class ClimateService {
    private static final int NOON = 6_000;
    private static final int CLIMATE_REGION_SIZE = 2_048;
    static final double COLD_SWEAT_CELSIUS_PER_MC = 25.0D;
    private static final double ABSOLUTE_WEATHER_VARIANCE_C = 5.0D;
    private static final double RELATIVE_WEATHER_VARIANCE = 0.10D;
    private static final int WEATHER_SPELL_DAYS = 4;
    private static final double DAILY_WEATHER_JITTER_C = 1.0D;
    private static final int STARTING_WEATHER_GRACE_DAYS = 5;
    private static final double STARTING_WEATHER_OFFSET_C = 5.0D;
    private static final int WEATHER_ANOMALY_TRANSITION_TICKS = 2_000;
    private static final float EQUATOR_WINTER_C = 24.0F;
    private static final float EQUATOR_SPRING_AUTUMN_C = 30.0F;
    private static final float EQUATOR_SUMMER_C = 40.5F;
    private static final float TEMPERATE_WINTER_C = 0.0F;
    private static final float TEMPERATE_SPRING_AUTUMN_C = 18.0F;
    private static final float TEMPERATE_SUMMER_C = 26.0F;
    private static final float POLE_WINTER_C = -37.5F;
    private static final float POLE_SPRING_AUTUMN_C = -12.0F;
    private static final float POLE_SUMMER_C = 5.0F;
    private static final float TEMPERATE_PLATEAU_MIN = 0.35F;
    private static final float TEMPERATE_PLATEAU_MAX = 0.65F;
    private static final float SNOW_THRESHOLD = 3.0F / 25.0F;
    private static final float PERSISTENT_SNOW_THRESHOLD = -3.0F / 25.0F;
    private static final TagKey<Biome> COLD = tag("c", "is_cold");
    private static final TagKey<Biome> COLD_OVERWORLD = tag("c", "is_cold/overworld");
    private static final TagKey<Biome> HOT = tag("c", "is_hot");
    private static final TagKey<Biome> HOT_OVERWORLD = tag("c", "is_hot/overworld");
    private static final TagKey<Biome> DRY = tag("c", "is_dry");
    private static final TagKey<Biome> DRY_OVERWORLD = tag("c", "is_dry/overworld");
    private static final TagKey<Biome> WET = tag("c", "is_wet");
    private static final TagKey<Biome> WET_OVERWORLD = tag("c", "is_wet/overworld");
    private static final TagKey<Biome> OCEAN = tag("minecraft", "is_ocean");
    private static final TagKey<Biome> MOUNTAIN = tag("minecraft", "is_mountain");

    private ClimateService() {
    }

    private static TagKey<Biome> tag(String namespace, String path) {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    public static ClimateSnapshot snapshot(Level level, Holder<Biome> biome, BlockPos pos) {
        return snapshot(level, biome, pos, isExposedToSky(level, pos));
    }

    public static ClimateSnapshot snapshot(Level level, Holder<Biome> biome, BlockPos pos, boolean exposedToSky) {
        ClimateSnapshot.WeatherState weatherState = weatherState(level, biome, exposedToSky);
        return snapshot(level, biome, pos, exposedToSky, weatherState, null, level.getDayTime());
    }

    public static ClimateBreakdown breakdown(Level level, Holder<Biome> biome, BlockPos pos) {
        return breakdown(level, biome, pos, isExposedToSky(level, pos));
    }

    public static ClimateBreakdown breakdown(Level level, Holder<Biome> biome, BlockPos pos, boolean exposedToSky) {
        ClimateSnapshot.WeatherState weatherState = weatherState(level, biome, exposedToSky);
        return breakdown(level, biome, pos, exposedToSky, weatherState, null, level.getDayTime());
    }

    public static ClimateSnapshot auditSnapshot(ServerLevel level, Holder<Biome> biome, BlockPos pos, boolean exposedToSky, @Nullable Float seasonFactorOverride, boolean raining) {
        ClimateSnapshot.WeatherState weatherState = raining ? ClimateSnapshot.WeatherState.RAIN : ClimateSnapshot.WeatherState.CLEAR;
        return auditSnapshot(level, biome, pos, exposedToSky, seasonFactorOverride, weatherState, NOON);
    }

    public static ClimateSnapshot auditSnapshot(ServerLevel level, Holder<Biome> biome, BlockPos pos, boolean exposedToSky, @Nullable Float seasonFactorOverride, ClimateSnapshot.WeatherState weatherState, long dayTime) {
        return snapshot(level, biome, pos, exposedToSky, weatherState, seasonFactorOverride, dayTime);
    }

    private static ClimateSnapshot snapshot(Level level, Holder<Biome> biome, BlockPos pos, boolean exposedToSky, ClimateSnapshot.WeatherState weatherState, @Nullable Float seasonFactorOverride, long dayTime) {
        weatherState = effectiveWeatherState(biome, exposedToSky, weatherState);
        float minecraftTemperature = minecraftTemperature(level, biome, pos, weatherState, seasonFactorOverride);
        float dayNightOffset = dayNightOffset(biome, dayTime);
        float weatherCompression = weatherCompression(weatherState, exposedToSky);
        float outdoorMinecraftTemperature = outdoorMinecraftTemperature(minecraftTemperature, dayNightOffset, weatherCompression);
        return new ClimateSnapshot(
                minecraftTemperature,
                clearOutdoorMinecraftTemperature(minecraftTemperature, dayNightOffset),
                outdoorMinecraftTemperature,
                dayNightOffset,
                weatherCompression,
                estimatedCelsius(outdoorMinecraftTemperature),
                rainCooling(weatherState, exposedToSky, biome),
                exposedToSky,
                weatherState,
                precipitationKind(biome, outdoorMinecraftTemperature),
                snowBehavior(outdoorMinecraftTemperature)
        );
    }

    private static ClimateBreakdown breakdown(Level level, Holder<Biome> biome, BlockPos pos, boolean exposedToSky, ClimateSnapshot.WeatherState weatherState, @Nullable Float seasonFactorOverride, long dayTime) {
        weatherState = effectiveWeatherState(biome, exposedToSky, weatherState);
        float poleFactor = poleFactor(pos);
        float latitudeMean = latitudeMean(poleFactor);
        float seasonFactor = seasonFactor(level, seasonFactorOverride);
        float seasonalScale = seasonalScale(poleFactor);
        float seasonOffset = seasonOffset(seasonFactor, poleFactor);
        float biomeOffset = biomeOffset(biome);
        float baseTemperature = baseTemperature(latitudeMean, seasonOffset, biomeOffset);
        float weatherOffset = climateAnomaly(level, pos, seasonFactor, weatherState, baseTemperature);
        float minecraftTemperature = baseTemperature + weatherOffset;
        float dayNightOffset = dayNightOffset(biome, dayTime);
        float weatherCompression = weatherCompression(weatherState, exposedToSky);
        float outdoorMinecraftTemperature = outdoorMinecraftTemperature(minecraftTemperature, dayNightOffset, weatherCompression);
        ClimateSnapshot snapshot = new ClimateSnapshot(
                minecraftTemperature,
                clearOutdoorMinecraftTemperature(minecraftTemperature, dayNightOffset),
                outdoorMinecraftTemperature,
                dayNightOffset,
                weatherCompression,
                estimatedCelsius(outdoorMinecraftTemperature),
                rainCooling(weatherState, exposedToSky, biome),
                exposedToSky,
                weatherState,
                precipitationKind(biome, outdoorMinecraftTemperature),
                snowBehavior(outdoorMinecraftTemperature)
        );
        return new ClimateBreakdown(
                poleFactor,
                latitudeMean,
                seasonFactor,
                seasonalScale,
                seasonOffset,
                biomeOffset,
                baseTemperature,
                weatherOffset,
                dayNightOffset,
                weatherCompression,
                snapshot
        );
    }

    public static float minecraftTemperature(Level level, Holder<Biome> biome, BlockPos pos) {
        return baseMinecraftTemperature(level, biome, pos);
    }

    public static float spawnTemperature(ServerLevel level, Holder<Biome> biome, BlockPos pos) {
        return outdoorMinecraftTemperature(level, biome, pos);
    }

    public static float outdoorMinecraftTemperature(Level level, Holder<Biome> biome, BlockPos pos) {
        return outdoorMinecraftTemperature(level, biome, pos, isExposedToSky(level, pos));
    }

    public static float outdoorMinecraftTemperature(Level level, Holder<Biome> biome, BlockPos pos, boolean exposedToSky) {
        return outdoorMinecraftTemperature(level, biome, pos, exposedToSky, weatherState(level, biome, exposedToSky));
    }

    public static float outdoorMinecraftTemperature(Level level, Holder<Biome> biome, BlockPos pos, boolean exposedToSky, ClimateSnapshot.WeatherState weatherState) {
        return outdoorMinecraftTemperature(level, biome, pos, exposedToSky, weatherState, null, level.getDayTime());
    }

    private static float outdoorMinecraftTemperature(Level level, Holder<Biome> biome, BlockPos pos, boolean exposedToSky, ClimateSnapshot.WeatherState weatherState, @Nullable Float seasonFactorOverride, long dayTime) {
        float minecraftTemperature = minecraftTemperature(level, biome, pos, weatherState, seasonFactorOverride);
        float dayNightOffset = dayNightOffset(biome, dayTime);
        return outdoorMinecraftTemperature(minecraftTemperature, dayNightOffset, weatherCompression(weatherState, exposedToSky));
    }

    public static float clearOutdoorMinecraftTemperature(Level level, Holder<Biome> biome, BlockPos pos) {
        return clearOutdoorMinecraftTemperature(level, biome, pos, isExposedToSky(level, pos));
    }

    public static float clearOutdoorMinecraftTemperature(Level level, Holder<Biome> biome, BlockPos pos, boolean exposedToSky) {
        ClimateSnapshot.WeatherState weatherState = weatherState(level, biome, exposedToSky);
        float minecraftTemperature = minecraftTemperature(level, biome, pos, weatherState, null);
        return clearOutdoorMinecraftTemperature(minecraftTemperature, dayNightOffset(biome, level.getDayTime()));
    }

    public static ClimateSnapshot.SnowBehavior snowBehaviorAt(Level level, Holder<Biome> biome, BlockPos pos) {
        return snowBehavior(outdoorMinecraftTemperature(level, biome, pos));
    }

    public static ClimateSnapshot.WeatherState weatherStateAt(Level level, Holder<Biome> biome, BlockPos pos) {
        return weatherState(level, biome, isExposedToSky(level, pos));
    }

    public static ClimateSnapshot.WeatherState weatherStateAt(Level level, Holder<Biome> biome, boolean exposedToSky) {
        return weatherState(level, biome, exposedToSky);
    }

    public static float rainCooledTemperature(float temperature) {
        return (temperature - 0.3F) * 2.0F;
    }

    public static float baseMinecraftTemperature(Level level, Holder<Biome> biome, BlockPos pos) {
        return arcticOutdoorMean(level, biome, pos, null);
    }

    private static float auditMinecraftTemperature(ServerLevel level, Holder<Biome> biome, BlockPos pos, boolean exposedToSky, @Nullable Float seasonFactorOverride, boolean raining) {
        return baseMinecraftTemperature(level, biome, pos, seasonFactorOverride);
    }

    private static float baseMinecraftTemperature(ServerLevel level, Holder<Biome> biome, BlockPos pos, @Nullable Float seasonFactorOverride) {
        return arcticOutdoorMean(level, biome, pos, seasonFactorOverride);
    }

    private static float arcticOutdoorMean(Level level, Holder<Biome> biome, BlockPos pos, @Nullable Float seasonFactorOverride) {
        float poleFactor = poleFactor(pos);
        float latitudeMean = latitudeMean(poleFactor);
        float seasonOffset = seasonOffset(seasonFactor(level, seasonFactorOverride), poleFactor);
        float biomeOffset = biomeOffset(biome);
        return baseTemperature(latitudeMean, seasonOffset, biomeOffset);
    }

    private static float poleFactor(BlockPos pos) {
        float distance = Calculations.distanceFactor(new ChunkPos(pos));
        return Mth.abs((distance - 0.5F) * 2.0F);
    }

    private static float latitudeMean(float poleFactor) {
        return minecraftTemperatureFromCelsius(springAutumnC(poleFactor));
    }

    private static float seasonalScale(float poleFactor) {
        return celsiusOffsetToMinecraftTemperatureOffset((summerC(poleFactor) - winterC(poleFactor)) * 0.5F);
    }

    private static float seasonOffset(float seasonFactor, float poleFactor) {
        float springAutumnC = springAutumnC(poleFactor);
        float seasonC = seasonFactor >= 0.0F
                ? Mth.lerp(seasonFactor, springAutumnC, summerC(poleFactor))
                : Mth.lerp(-seasonFactor, springAutumnC, winterC(poleFactor));
        return celsiusOffsetToMinecraftTemperatureOffset(seasonC - springAutumnC);
    }

    private static float winterC(float poleFactor) {
        return latitudeTargetC(poleFactor, EQUATOR_WINTER_C, TEMPERATE_WINTER_C, POLE_WINTER_C);
    }

    private static float springAutumnC(float poleFactor) {
        return latitudeTargetC(poleFactor, EQUATOR_SPRING_AUTUMN_C, TEMPERATE_SPRING_AUTUMN_C, POLE_SPRING_AUTUMN_C);
    }

    private static float summerC(float poleFactor) {
        return latitudeTargetC(poleFactor, EQUATOR_SUMMER_C, TEMPERATE_SUMMER_C, POLE_SUMMER_C);
    }

    private static float latitudeTargetC(float poleFactor, float equatorC, float temperateC, float poleC) {
        float equatorBlend = 1.0F - climateEase(poleFactor / TEMPERATE_PLATEAU_MIN);
        float poleBlend = climateEase((poleFactor - TEMPERATE_PLATEAU_MAX) / (1.0F - TEMPERATE_PLATEAU_MAX));
        float temperateBlend = Mth.clamp(1.0F - equatorBlend - poleBlend, 0.0F, 1.0F);
        return equatorBlend * equatorC + temperateBlend * temperateC + poleBlend * poleC;
    }

    private static float climateEase(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return (1.0F - Mth.cos(t * Mth.PI)) * 0.5F;
    }

    private static float biomeOffset(Holder<Biome> biome) {
        float biomeTemperature = biome.value().getBaseTemperature();
        return Mth.clamp(biomeTemperature - 0.8F, -0.45F, 0.45F) * 0.4F;
    }

    private static float baseTemperature(Holder<Biome> biome, BlockPos pos, float seasonFactor) {
        float poleFactor = poleFactor(pos);
        return baseTemperature(latitudeMean(poleFactor), seasonOffset(seasonFactor, poleFactor), biomeOffset(biome));
    }

    private static float baseTemperature(float latitudeMean, float seasonOffset, float biomeOffset) {
        return Mth.clamp(latitudeMean + seasonOffset + biomeOffset, -1.8F, 1.8F);
    }

    private static float minecraftTemperature(Level level, Holder<Biome> biome, BlockPos pos, ClimateSnapshot.WeatherState weatherState, @Nullable Float seasonFactorOverride) {
        float seasonFactor = seasonFactor(level, seasonFactorOverride);
        float baseTemperature = baseTemperature(biome, pos, seasonFactor);
        return baseTemperature + climateAnomaly(level, pos, seasonFactor, weatherState, baseTemperature);
    }

    private static float seasonFactor(Level level, @Nullable Float seasonFactorOverride) {
        if (seasonFactorOverride != null) return seasonFactorOverride;
        return Calculations.seasonalFactor(level);
    }

    private static float climateAnomaly(Level level, BlockPos pos, float seasonFactor, ClimateSnapshot.WeatherState weatherState, float baseTemperature) {
        float debugWeatherOffset = ClimateDebugWeatherOverrides.minecraftOffset(level);
        if (!Float.isNaN(debugWeatherOffset)) return debugWeatherOffset;

        float winterInfluence = Mth.clamp(-seasonFactor, 0.0F, 1.0F);
        float shoulderInfluence = 1.0F - Mth.abs(seasonFactor);
        float maxAmplitude = maxWeatherVariance(baseTemperature);
        float seasonalScale = Mth.clamp(0.65F + winterInfluence * 0.35F + shoulderInfluence * 0.15F, 0.0F, 1.0F);
        float weatherScale = switch (weatherState) {
            case CLEAR -> 0.6F;
            case RAIN -> 0.85F;
            case THUNDER -> 1.0F;
        };
        float amplitude = maxAmplitude * seasonalScale * weatherScale;
        float anomaly = interpolatedWeatherAnomaly(level.getDayTime(), pos, amplitude);

        if (weatherState != ClimateSnapshot.WeatherState.CLEAR) {
            if (anomaly < 0.0F) {
                anomaly *= weatherState == ClimateSnapshot.WeatherState.THUNDER ? 1.25F : 1.1F;
            } else {
                anomaly *= weatherState == ClimateSnapshot.WeatherState.THUNDER ? 0.5F : 0.7F;
            }
        }

        anomaly = Mth.clamp(anomaly, -maxAmplitude, maxAmplitude);
        return Mth.clamp(startingWeatherBlend(level.getGameTime(), anomaly), -maxAmplitude, maxAmplitude);
    }

    private static float maxWeatherVariance(float baseTemperature) {
        double baseCelsius = estimatedCelsius(baseTemperature);
        return (float) ((ABSOLUTE_WEATHER_VARIANCE_C + Math.abs(baseCelsius) * RELATIVE_WEATHER_VARIANCE) / COLD_SWEAT_CELSIUS_PER_MC);
    }

    private static float interpolatedWeatherAnomaly(long dayTime, BlockPos pos, float amplitude) {
        long shiftedTime = dayTime - NOON;
        long cycle = Math.floorDiv(shiftedTime, 24_000L);
        long ticksSinceNoon = Math.floorMod(shiftedTime, 24_000L);
        long transitionStart = 24_000L - WEATHER_ANOMALY_TRANSITION_TICKS;
        if (ticksSinceNoon < transitionStart) {
            return dailyWeatherAnomaly(cycle, pos, amplitude);
        }

        float progress = (ticksSinceNoon - transitionStart) / (float) WEATHER_ANOMALY_TRANSITION_TICKS;
        float blend = progress * progress * (3.0F - 2.0F * progress);
        return Mth.lerp(blend, dailyWeatherAnomaly(cycle, pos, amplitude), dailyWeatherAnomaly(cycle + 1L, pos, amplitude));
    }

    private static float dailyWeatherAnomaly(long cycle, BlockPos pos, float amplitude) {
        return weatherSpellCurve(cycle, pos) * amplitude + dailyWeatherJitter(cycle, pos);
    }

    private static float weatherSpellCurve(long cycle, BlockPos pos) {
        long spell = Math.floorDiv(cycle, WEATHER_SPELL_DAYS);
        int dayInSpell = Math.floorMod(cycle, WEATHER_SPELL_DAYS);
        float progress = dayInSpell / (float) WEATHER_SPELL_DAYS;
        float blend = progress * progress * (3.0F - 2.0F * progress);
        return Mth.lerp(blend, regionalWeatherNoise(spell, pos, 0L), regionalWeatherNoise(spell + 1L, pos, 0L));
    }

    private static float dailyWeatherJitter(long cycle, BlockPos pos) {
        return celsiusOffsetToMinecraftTemperatureOffset(regionalWeatherNoise(cycle, pos, 2L) * DAILY_WEATHER_JITTER_C);
    }

    private static float startingWeatherBlend(long gameTime, float anomaly) {
        long worldDay = Math.max(0L, Math.floorDiv(gameTime, 24_000L));
        if (worldDay >= STARTING_WEATHER_GRACE_DAYS) return anomaly;

        float blend = worldDay / (float) STARTING_WEATHER_GRACE_DAYS;
        return Mth.lerp(blend, celsiusOffsetToMinecraftTemperatureOffset(STARTING_WEATHER_OFFSET_C), anomaly);
    }

    private static float regionalWeatherNoise(long cycle, BlockPos pos, long salt) {
        double regionX = pos.getX() / (double) CLIMATE_REGION_SIZE;
        double regionZ = pos.getZ() / (double) CLIMATE_REGION_SIZE;
        double broad = smoothWeatherNoise(cycle, regionX, regionZ, salt);
        double edgeJitter = smoothWeatherNoise(cycle, regionX * 4.0D + 19.37D, regionZ * 4.0D - 7.11D, salt + 1L);
        return (float) Mth.clamp(broad * 0.75D + edgeJitter * 0.25D, -1.0D, 1.0D);
    }

    private static double smoothWeatherNoise(long cycle, double x, double z, long salt) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        int x1 = x0 + 1;
        int z1 = z0 + 1;
        double xBlend = fade(x - x0);
        double zBlend = fade(z - z0);
        double north = lerp(xBlend, latticeWeatherNoise(cycle, x0, z0, salt), latticeWeatherNoise(cycle, x1, z0, salt));
        double south = lerp(xBlend, latticeWeatherNoise(cycle, x0, z1, salt), latticeWeatherNoise(cycle, x1, z1, salt));
        return lerp(zBlend, north, south);
    }

    private static double latticeWeatherNoise(long cycle, int regionX, int regionZ, long salt) {
        long hash = mix64(cycle * 0x9E3779B97F4A7C15L
                ^ (long) regionX * 0xBF58476D1CE4E5B9L
                ^ (long) regionZ * 0x94D049BB133111EBL
                ^ salt * 0xD6E8FEB86659FD93L);
        double unit = (hash >>> 11) * 0x1.0p-53;
        return unit * 2.0D - 1.0D;
    }

    private static double fade(double value) {
        return value * value * value * (value * (value * 6.0D - 15.0D) + 10.0D);
    }

    private static double lerp(double blend, double from, double to) {
        return from + blend * (to - from);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public static boolean isRainCooling(Level level, Holder<Biome> biome, BlockPos pos) {
        return isRainCooling(level, biome, isExposedToSky(level, pos));
    }

    public static boolean isRainCooling(Level level, Holder<Biome> biome, boolean exposedToSky) {
        return level.isRaining() && exposedToSky && biome.value().hasPrecipitation();
    }

    private static boolean rainCooling(ClimateSnapshot.WeatherState weatherState, boolean exposedToSky, Holder<Biome> biome) {
        return weatherState != ClimateSnapshot.WeatherState.CLEAR && exposedToSky && biome.value().hasPrecipitation();
    }

    private static ClimateSnapshot.WeatherState weatherState(Level level, Holder<Biome> biome, boolean exposedToSky) {
        if (!level.isRaining()) return ClimateSnapshot.WeatherState.CLEAR;
        return effectiveWeatherState(biome, exposedToSky, level.isThundering()
                ? ClimateSnapshot.WeatherState.THUNDER
                : ClimateSnapshot.WeatherState.RAIN);
    }

    private static ClimateSnapshot.WeatherState effectiveWeatherState(Holder<Biome> biome, boolean exposedToSky, ClimateSnapshot.WeatherState weatherState) {
        if (weatherState == ClimateSnapshot.WeatherState.CLEAR) return ClimateSnapshot.WeatherState.CLEAR;
        if (!exposedToSky || !biome.value().hasPrecipitation()) return ClimateSnapshot.WeatherState.CLEAR;
        return weatherState;
    }

    public static boolean isExposedToSky(Level level, BlockPos pos) {
        return level.getBrightness(LightLayer.SKY, pos) != 0;
    }

    private static float dayNightOffset(Holder<Biome> biome, long dayTime) {
        double dayProgress = Math.floorMod(dayTime, 24_000L) / 24_000.0D;
        double noonWarmth = Math.cos((dayProgress - 0.25D) * Math.PI * 2.0D);
        return (float) (noonWarmth * dayNightAmplitude(biome));
    }

    private static float dayNightAmplitude(Holder<Biome> biome) {
        if (biome.is(WET) || biome.is(WET_OVERWORLD) || biome.is(OCEAN) || biome.is(BiomeTags.IS_JUNGLE)) return 0.10F;
        if (biome.is(COLD) || biome.is(COLD_OVERWORLD) || biome.is(MOUNTAIN) || biome.is(BiomeTags.IS_TAIGA)) return 0.18F;
        if (biome.is(HOT) || biome.is(HOT_OVERWORLD) || biome.is(DRY) || biome.is(DRY_OVERWORLD) || biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.IS_SAVANNA)) return 0.56F;
        return 0.28F;
    }

    private static float weatherCompression(ClimateSnapshot.WeatherState weatherState, boolean exposedToSky) {
        if (!exposedToSky) return 0.0F;
        return switch (weatherState) {
            case CLEAR -> 0.0F;
            case RAIN -> 0.55F;
            case THUNDER -> 0.75F;
        };
    }

    private static float outdoorMinecraftTemperature(float minecraftTemperature, float dayNightOffset, float weatherCompression) {
        return minecraftTemperature + dayNightOffset * (1.0F - weatherCompression);
    }

    private static float clearOutdoorMinecraftTemperature(float minecraftTemperature, float dayNightOffset) {
        return minecraftTemperature + dayNightOffset;
    }

    public static ClimateSnapshot.PrecipitationKind precipitationKind(Holder<Biome> biome, float minecraftTemperature) {
        if (!biome.value().hasPrecipitation()) return ClimateSnapshot.PrecipitationKind.NONE;
        return minecraftTemperature < SNOW_THRESHOLD ? ClimateSnapshot.PrecipitationKind.SNOW : ClimateSnapshot.PrecipitationKind.RAIN;
    }

    public static Biome.Precipitation precipitationAtAsVanillaType(LevelReader levelReader, Holder<Biome> biome, BlockPos pos) {
        if (!biome.value().hasPrecipitation()) return Biome.Precipitation.NONE;
        if (!(levelReader instanceof Level level)) return biome.value().getPrecipitationAt(pos);
        return switch (precipitationKind(biome, outdoorMinecraftTemperature(level, biome, pos, true))) {
            case NONE -> Biome.Precipitation.NONE;
            case RAIN -> Biome.Precipitation.RAIN;
            case SNOW -> Biome.Precipitation.SNOW;
        };
    }

    public static boolean hasSnowPrecipitation(LevelReader levelReader, Holder<Biome> biome, BlockPos pos) {
        return precipitationAtAsVanillaType(levelReader, biome, pos) == Biome.Precipitation.SNOW;
    }

    public static boolean shouldFreeze(LevelReader levelReader, Holder<Biome> biome, BlockPos water) {
        return shouldFreeze(levelReader, biome, water, true);
    }

    public static boolean shouldFreeze(LevelReader levelReader, Holder<Biome> biome, BlockPos water, boolean mustBeAtEdge) {
        if (warmEnoughToRain(levelReader, biome, water)) return false;
        if (water.getY() < levelReader.getMinBuildHeight()
                || water.getY() >= levelReader.getMaxBuildHeight()
                || levelReader.getBrightness(LightLayer.BLOCK, water) >= 10) {
            return false;
        }

        BlockState blockState = levelReader.getBlockState(water);
        FluidState fluidState = levelReader.getFluidState(water);
        if (fluidState.getType() != Fluids.WATER || !(blockState.getBlock() instanceof LiquidBlock)) return false;
        if (!mustBeAtEdge) return true;

        boolean surroundedByWater = levelReader.isWaterAt(water.west())
                && levelReader.isWaterAt(water.east())
                && levelReader.isWaterAt(water.north())
                && levelReader.isWaterAt(water.south());
        return !surroundedByWater;
    }

    public static boolean shouldSnow(LevelReader levelReader, Holder<Biome> biome, BlockPos pos) {
        if (warmEnoughToRain(levelReader, biome, pos)) return false;
        if (pos.getY() < levelReader.getMinBuildHeight()
                || pos.getY() >= levelReader.getMaxBuildHeight()
                || levelReader.getBrightness(LightLayer.BLOCK, pos) >= 10) {
            return false;
        }

        BlockState blockState = levelReader.getBlockState(pos);
        return (blockState.isAir()
                || (blockState.is(Blocks.SNOW) && SnowAccumulation.canAccumulate(levelReader, pos, blockState))
                || SeasonalGroundCover.canBeBuriedBySnow(blockState))
                && Blocks.SNOW.defaultBlockState().canSurvive(levelReader, pos);
    }

    public static boolean warmEnoughToRain(LevelReader levelReader, Holder<Biome> biome, BlockPos pos) {
        if (!(levelReader instanceof Level level)) return biome.value().warmEnoughToRain(pos);
        return outdoorMinecraftTemperature(level, biome, pos, true) >= SNOW_THRESHOLD;
    }

    public static ClimateSnapshot.SnowBehavior snowBehavior(float minecraftTemperature) {
        if (minecraftTemperature < PERSISTENT_SNOW_THRESHOLD) return ClimateSnapshot.SnowBehavior.PERSISTENT;
        if (minecraftTemperature < SNOW_THRESHOLD) return ClimateSnapshot.SnowBehavior.TRANSITIONAL_SURFACE;
        return ClimateSnapshot.SnowBehavior.MELTS;
    }

    public static double estimatedCelsius(float minecraftTemperature) {
        return Math.round((minecraftTemperature * COLD_SWEAT_CELSIUS_PER_MC) * 10.0D) / 10.0D;
    }

    public static float minecraftTemperatureFromCelsius(double celsius) {
        return (float) (celsius / COLD_SWEAT_CELSIUS_PER_MC);
    }

    public static float celsiusOffsetToMinecraftTemperatureOffset(double celsiusOffset) {
        return (float) (celsiusOffset / COLD_SWEAT_CELSIUS_PER_MC);
    }

    public static void setDebugWeatherOffsetCelsius(MinecraftServer server, double celsiusOffset) {
        ClimateDebugWeatherOverrides.setCelsius(server, celsiusOffset);
    }

    public static void clearDebugWeatherOffset(MinecraftServer server) {
        ClimateDebugWeatherOverrides.clear(server);
    }

    public static boolean hasDebugWeatherOffset(MinecraftServer server) {
        return ClimateDebugWeatherOverrides.has(server);
    }

    public static double debugWeatherOffsetCelsius(MinecraftServer server) {
        return ClimateDebugWeatherOverrides.celsiusOffset(server);
    }

    public record ClimateBreakdown(
            float poleFactor,
            float latitudeMean,
            float seasonFactor,
            float seasonalScale,
            float seasonOffset,
            float biomeOffset,
            float baseTemperature,
            float weatherOffset,
            float dayNightOffset,
            float weatherCompression,
            ClimateSnapshot snapshot
    ) {
    }

}
