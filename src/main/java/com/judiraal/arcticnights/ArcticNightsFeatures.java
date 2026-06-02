package com.judiraal.arcticnights;

public final class ArcticNightsFeatures {
    private ArcticNightsFeatures() {
    }

    public static boolean climateSystem() {
        return ArcticNightsConfig.climateSystem.getAsBoolean();
    }

    public static boolean climateMaintenanceCommands() {
        return ArcticNightsConfig.climateMaintenanceCommands.getAsBoolean();
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

    public static boolean tickrateClimateCatchupDiagnostics() {
        return ArcticNightsConfig.tickrateCatchupDiagnostics.getAsBoolean();
    }

    public static boolean visualSnowCover() {
        return ArcticNightsConfig.visualSnowCover.getAsBoolean();
    }

    public static boolean visualSnowCoverPipelineSubstitution() {
        return ArcticNightsConfig.visualSnowCoverPipelineSubstitution.getAsBoolean();
    }

    public static int visualSnowCoverRequiredAdjacentLayers() {
        return ArcticNightsConfig.visualSnowCoverRequiredAdjacentLayers.getAsInt();
    }

    public static boolean visualSnowCoverCountEligibleNeighbors() {
        return ArcticNightsConfig.visualSnowCoverCountEligibleNeighbors.getAsBoolean();
    }

    public static int visualSnowCoverRequiredRealAdjacentLayers() {
        return ArcticNightsConfig.visualSnowCoverRequiredRealAdjacentLayers.getAsInt();
    }
}
