package com.judiraal.arcticnights.util;

import java.util.List;

final class BiomeArchetypes {
    private BiomeArchetypes() {
    }

    static String classify(String biomeId, List<String> tags) {
        if (biomeId.contains("alpha_islands_winter")) return "ocean_cold_frozen";
        if (biomeId.contains("alpha_islands")) return "ocean_temperate_warm";
        if (has(tags, "terralith:caves", "terralith:reference/cave", "terralith:reference/deep_cave") || biomeId.contains("cave") || biomeId.endsWith(":deep_dark")) {
            return "cave_deep";
        }
        if (has(tags, "terralith:volcanic") || biomeId.contains("volcanic")) {
            return "volcanic_geothermal";
        }
        if (has(tags, "c:is_icy", "c:is_snowy", "c:is_aquatic_icy", "minecraft:is_snowy", "terralith:reference/temperature/frozen_all", "terralith:reference/temperature/frozen_with_structures")) {
            if (has(tags, "minecraft:is_ocean", "c:is_shallow_ocean", "c:is_deep_ocean")) return "ocean_cold_frozen";
            return "alpine_cold";
        }
        if (has(tags, "c:is_cold", "c:is_cold/overworld", "terralith:reference/temperature/cold")) {
            if (has(tags, "minecraft:is_ocean", "c:is_shallow_ocean", "c:is_deep_ocean")) return "ocean_cold_frozen";
            return "cool_taiga";
        }
        if (has(tags, "minecraft:is_ocean", "c:is_shallow_ocean", "c:is_deep_ocean")) {
            return "ocean_temperate_warm";
        }
        if (has(tags, "minecraft:is_beach", "minecraft:is_river", "c:is_beach") || biomeId.contains("beach") || biomeId.contains("river")) {
            return "coastal_wet";
        }
        if (has(tags, "minecraft:is_jungle", "c:is_swamp", "c:is_wet", "c:is_wet/overworld", "terralith:reference/jungle", "terralith:reference/swamp", "terralith:reference/temperature/lukewarm", "terralith:reference/temperature/warm")) {
            return "warm_humid";
        }
        if (has(tags, "minecraft:is_badlands", "minecraft:is_savanna", "c:is_hot", "c:is_hot/overworld", "c:is_dry", "c:is_dry/overworld", "terralith:reference/desert_all", "terralith:reference/savanna")) {
            return "hot_arid";
        }
        if (has(tags, "minecraft:is_mountain", "c:is_mountain", "c:is_mountain/peak", "c:is_mountain/slope", "terralith:reference/mountain_peak", "terralith:reference/mountain_slope", "terralith:reference/windswept")) {
            return "alpine_cold";
        }
        if (has(tags, "minecraft:is_taiga", "c:is_taiga")) {
            return "cool_taiga";
        }
        if (has(tags, "minecraft:is_forest", "c:is_forest", "terralith:reference/forest")) {
            return "temperate_forest";
        }
        if (has(tags, "terralith:reference/plains", "terralith:reference/temperature/temperate") || biomeId.contains("plains") || biomeId.contains("meadow")) {
            return "temperate_open";
        }
        return "unclassified";
    }

    static boolean hasFrozenStory(List<String> tags) {
        return has(tags, "c:is_icy", "c:is_snowy", "c:is_aquatic_icy", "minecraft:is_snowy", "terralith:reference/temperature/frozen_all", "terralith:reference/temperature/frozen_with_structures");
    }

    static List<String> climateTags(List<String> tags) {
        return tags.stream()
                .filter(tag -> tag.contains("temperature")
                        || tag.contains("is_cold")
                        || tag.contains("is_hot")
                        || tag.contains("is_wet")
                        || tag.contains("is_dry")
                        || tag.contains("is_snowy")
                        || tag.contains("is_icy")
                        || tag.contains("is_ocean")
                        || tag.contains("is_mountain")
                        || tag.contains("is_forest")
                        || tag.contains("is_taiga")
                        || tag.contains("is_jungle")
                        || tag.contains("is_savanna")
                        || tag.contains("is_badlands")
                        || tag.contains("is_beach")
                        || tag.contains("is_river")
                        || tag.contains("cave")
                        || tag.contains("volcanic"))
                .distinct()
                .sorted()
                .toList();
    }

    private static boolean has(List<String> tags, String... wanted) {
        for (String tag : wanted) if (tags.contains(tag)) return true;
        return false;
    }
}
