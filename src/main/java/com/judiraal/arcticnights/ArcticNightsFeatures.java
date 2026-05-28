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

    public static boolean spawnDiagnostics() {
        return ArcticNightsConfig.spawnDiagnostics.getAsBoolean();
    }

    public static boolean coldSweatClientDiagnostics() {
        return ArcticNightsConfig.coldSweatClientDiagnostics.getAsBoolean();
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

    public static boolean tickrateClimateCatchup() {
        return climateSystem() && ArcticNightsConfig.tickrateClimateCatchup.getAsBoolean();
    }

    public static boolean tickrateClimateCatchupDebug() {
        return ArcticNightsConfig.tickrateClimateCatchupDebug.getAsBoolean();
    }
}
