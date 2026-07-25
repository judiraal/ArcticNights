package com.judiraal.arcticnights;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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

    public static boolean tornadoSeasonEnabled() {
        return ArcticNightsConfig.tornadoSeasonEnabled.getAsBoolean();
    }

    public static int tornadoGraceYears() {
        return ArcticNightsConfig.tornadoGraceYears.getAsInt();
    }

    public static Set<String> tornadoAllowedSubSeasons() {
        return ArcticNightsConfig.tornadoAllowedSubSeasons.get().stream()
                .map(String::trim)
                .map(name -> name.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public static double tornadoNaturalChanceMultiplier() {
        return ArcticNightsConfig.tornadoNaturalChanceMultiplier.getAsDouble();
    }

    public static int daysPerSeason() {
        return ArcticNightsConfig.daysPerSeason.getAsInt();
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
