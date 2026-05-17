package com.judiraal.arcticnights.util;

import java.util.ArrayList;
import java.util.List;

final class ClimateAuditFlags {
    private ClimateAuditFlags() {
    }

    static List<String> rowFlags(List<String> tags, String archetype, String season, String weather, boolean exposedToSky, ClimateSnapshot snapshot, OutdoorTemperatureRangeC coldSweatOutdoor) {
        List<String> flags = new ArrayList<>();
        if ("unclassified".equals(archetype)) flags.add("unclassified_biome");
        if (exposedToSky && snapshot.estimatedCelsius() > 3.0D && snapshot.snowBehavior() != ClimateSnapshot.SnowBehavior.MELTS) {
            flags.add("snow_above_3c");
        }
        if (exposedToSky && snapshot.estimatedCelsius() < -3.0D && snapshot.precipitationKind() == ClimateSnapshot.PrecipitationKind.RAIN) {
            flags.add("rain_below_minus_3c");
        }
        if ((tags.contains("c:is_hot") || tags.contains("c:is_hot/overworld"))
                && archetype.contains("cold")
                && !BiomeArchetypes.hasFrozenStory(tags)) {
            flags.add("hot_tag_cold_archetype");
        }
        if ((tags.contains("c:is_cold") || tags.contains("c:is_cold/overworld")) && ("warm_humid".equals(archetype) || "hot_arid".equals(archetype))) {
            flags.add("cold_tag_warm_archetype");
        }
        if ("winter".equals(season) && "temperate_forest".equals(archetype) && "clear".equals(weather) && snapshot.estimatedCelsius() > 8.0D) {
            flags.add("warm_temperate_winter");
        }
        if ("summer".equals(season) && ("alpine_cold".equals(archetype) || "ocean_cold_frozen".equals(archetype)) && snapshot.estimatedCelsius() > 16.0D) {
            flags.add("hot_cold_archetype_summer");
        }
        if (exposedToSky && coldSweatOutdoor != null && coldSweatOutdoor.noonC() > 3.0D && snapshot.snowBehavior() != ClimateSnapshot.SnowBehavior.MELTS) {
            flags.add("cold_sweat_config_noon_snow_above_3c");
        }
        if (exposedToSky && coldSweatOutdoor != null && coldSweatOutdoor.nightC() < -3.0D && snapshot.precipitationKind() == ClimateSnapshot.PrecipitationKind.RAIN) {
            flags.add("cold_sweat_config_night_rain_below_minus_3c");
        }
        return flags;
    }

    static boolean isPrimaryFlag(String flag) {
        return !isReferenceFlag(flag) && !isHypotheticalLatitudeFlag(flag);
    }

    static boolean isReferenceFlag(String flag) {
        return flag.startsWith("cold_sweat_config_");
    }

    static boolean isHypotheticalLatitudeFlag(String flag) {
        return "warm_temperate_winter".equals(flag) || "hot_cold_archetype_summer".equals(flag);
    }
}
