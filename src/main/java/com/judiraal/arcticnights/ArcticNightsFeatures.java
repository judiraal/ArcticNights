package com.judiraal.arcticnights;

public final class ArcticNightsFeatures {
    private ArcticNightsFeatures() {
    }

    public static boolean climateSystem() {
        return ArcticNightsConfig.climateSystem.getAsBoolean();
    }

    public static boolean debugCommands() {
        return ArcticNightsConfig.debugCommands.getAsBoolean();
    }

    public static boolean arcticSpawning() {
        return ArcticNightsConfig.arcticSpawning.getAsBoolean();
    }

    public static boolean fullMelting() {
        return ArcticNights.SERENE_SEASONS && ArcticNightsConfig.fullMelting.getAsBoolean();
    }

    public static boolean fullMeltingUsesClimate() {
        return fullMelting() && climateSystem();
    }
}
