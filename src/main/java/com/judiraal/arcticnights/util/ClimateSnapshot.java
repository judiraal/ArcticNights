package com.judiraal.arcticnights.util;

public record ClimateSnapshot(
        float minecraftTemperature,
        float clearOutdoorMinecraftTemperature,
        float outdoorMinecraftTemperature,
        float dayNightOffset,
        float weatherCompression,
        double estimatedCelsius,
        boolean rainCooling,
        boolean exposedToSky,
        WeatherState weatherState,
        PrecipitationKind precipitationKind,
        SnowBehavior snowBehavior
) {
    public enum WeatherState {
        CLEAR,
        RAIN,
        THUNDER
    }

    public enum PrecipitationKind {
        NONE,
        RAIN,
        SNOW
    }

    public enum SnowBehavior {
        PERSISTENT,
        TRANSITIONAL_SURFACE,
        MELTS
    }
}
