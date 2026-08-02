package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.Optional;

enum CaveTemperatureProfile {
    COLD("cold", 0.0D, new CelsiusTemperatureResponse(10.0D, 40.0D, 0.0D, 12.0D)),
    COOL("cool", -3.0D),
    MILD_COOL("mild_cool", -2.0D),
    WARM("warm", 8.0D),
    HOT("hot", 12.0D);

    private static final String TAG_PATH_PREFIX = "cave_temperature/";
    private static final CaveTemperatureProfile[] VALUES = values();

    private final TagKey<Biome> biomeTag;
    private final double celsiusOffset;
    private final float minecraftOffset;
    private final Optional<CelsiusTemperatureResponse> temperatureResponse;

    CaveTemperatureProfile(String tagPath, double celsiusOffset) {
        this(tagPath, celsiusOffset, null);
    }

    CaveTemperatureProfile(String tagPath, double celsiusOffset, CelsiusTemperatureResponse temperatureResponse) {
        this.biomeTag = TagKey.create(
                Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(ArcticNights.MOD_ID, TAG_PATH_PREFIX + tagPath)
        );
        this.celsiusOffset = celsiusOffset;
        this.minecraftOffset = ClimateService.celsiusOffsetToMinecraftTemperatureOffset(celsiusOffset);
        this.temperatureResponse = Optional.ofNullable(temperatureResponse);
    }

    static float minecraftOffset(Holder<Biome> biome) {
        for (CaveTemperatureProfile profile : VALUES) {
            if (biome.is(profile.biomeTag)) {
                return profile.minecraftOffset;
            }
        }
        return 0.0F;
    }

    static boolean hasColdProfile(Holder<Biome> biome) {
        return biome.is(COLD.biomeTag);
    }

    static double applyTemperatureResponse(Holder<Biome> biome, double minecraftTemperature) {
        for (CaveTemperatureProfile profile : VALUES) {
            if (biome.is(profile.biomeTag) && profile.temperatureResponse.isPresent()) {
                return profile.temperatureResponse.orElseThrow().applyMinecraftTemperature(minecraftTemperature);
            }
        }
        return minecraftTemperature;
    }

    TagKey<Biome> biomeTag() {
        return biomeTag;
    }

    double celsiusOffset() {
        return celsiusOffset;
    }

    Optional<CelsiusTemperatureResponse> temperatureResponse() {
        return temperatureResponse;
    }

    record CelsiusTemperatureResponse(
            double sourceMinimumCelsius,
            double sourceMaximumCelsius,
            double resultMinimumCelsius,
            double resultMaximumCelsius
    ) {
        CelsiusTemperatureResponse {
            if (!Double.isFinite(sourceMinimumCelsius)
                    || !Double.isFinite(sourceMaximumCelsius)
                    || !Double.isFinite(resultMinimumCelsius)
                    || !Double.isFinite(resultMaximumCelsius)) {
                throw new IllegalArgumentException("cave temperature response values must be finite");
            }
            if (sourceMinimumCelsius >= sourceMaximumCelsius) {
                throw new IllegalArgumentException("cave temperature response source minimum must be below its maximum");
            }
            if (resultMinimumCelsius > resultMaximumCelsius) {
                throw new IllegalArgumentException("cave temperature response result minimum cannot exceed its maximum");
            }
        }

        double applyMinecraftTemperature(double minecraftTemperature) {
            double sourceMinimum = ClimateService.minecraftTemperatureFromCelsius(sourceMinimumCelsius);
            double sourceMaximum = ClimateService.minecraftTemperatureFromCelsius(sourceMaximumCelsius);
            double resultMinimum = ClimateService.minecraftTemperatureFromCelsius(resultMinimumCelsius);
            double resultMaximum = ClimateService.minecraftTemperatureFromCelsius(resultMaximumCelsius);
            double progress = (minecraftTemperature - sourceMinimum) / (sourceMaximum - sourceMinimum);
            return resultMinimum + progress * (resultMaximum - resultMinimum);
        }
    }
}
