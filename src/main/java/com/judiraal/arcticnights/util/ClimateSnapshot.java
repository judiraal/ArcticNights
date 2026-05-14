package com.judiraal.arcticnights.util;

public record ClimateSnapshot(
        float minecraftTemperature,
        double estimatedCelsius,
        boolean rainCooling,
        PrecipitationKind precipitationKind,
        SnowBehavior snowBehavior
) {
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
