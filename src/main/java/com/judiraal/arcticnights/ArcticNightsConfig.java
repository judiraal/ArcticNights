package com.judiraal.arcticnights;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public class ArcticNightsConfig {
    public static final ArcticNightsConfig CONFIG;
    static final ModConfigSpec SPEC;

    static {
        Pair<ArcticNightsConfig, ModConfigSpec> pair =
                new ModConfigSpec.Builder().configure(ArcticNightsConfig::new);
        CONFIG = pair.getLeft();
        SPEC = pair.getRight();
    }

    public static ModConfigSpec.IntValue circumferenceBlockDistance;
    public static ModConfigSpec.BooleanValue bandedTemperature;
    public static ModConfigSpec.BooleanValue useLegacyAlgorithm;
    public static ModConfigSpec.IntValue firstTimeOfDay;
    public static ModConfigSpec.BooleanValue shaderSunPathRotation;
    public static ModConfigSpec.IntValue daysPerSeason;
    public static ModConfigSpec.ConfigValue<List<? extends Integer>> structureDenyRange;
    public static ModConfigSpec.BooleanValue arcticSpawning;
    public static ModConfigSpec.BooleanValue fullMelting;
    public static ModConfigSpec.BooleanValue alternativeTornadoStrengthGrabbing;

    private ArcticNightsConfig(final ModConfigSpec.Builder builder) {
        circumferenceBlockDistance = builder.comment("The distance in blocks for a full circumference, i.e. double the distance between two poles.")
                .defineInRange("circumferenceBlockDistance", 40000, 4000, 400000);
        bandedTemperature = builder.comment("Whether temperature-based biome distribution should follow circumference.")
                .define("bandedTemperature", true);
        useLegacyAlgorithm = builder.comment("Use the legacy algorithm that relies on sampler mixin.")
                .define("useLegacyAlgorithm", true);
        firstTimeOfDay = builder.comment("First time of day in game ticks when a new game is started.")
                .defineInRange("firstTimeOfDay", 1000, 0, 12000);
        shaderSunPathRotation = builder.comment("Enable dynamic sunPathRotation for Iris shaders.")
                .define("shaderSunPathRotation", true);
        daysPerSeason = builder.comment(
                "When no seasons mod is detected this is the number of days in a season.",
                        "Set to 0 to disable all seasonal effects.")
                .defineInRange("daysPerSeason", 24, 0, 100);
        structureDenyRange = builder.comment("Deny structures between these z block-coordinates.",
                        " Default: [0, 0]",
                        " Example: [-6000, 16000]")
                .define("structureDenyRange", () -> Arrays.asList(0, 0), value -> {
                    if (value instanceof List<?> l && l.size() == 2) {
                        return ((Integer) l.get(0)) <= ((Integer) l.get(1));
                    }
                    return false;
                });
        arcticSpawning = builder.comment("Enable tag-based seasonal spawning rules, e.g. require cold for undead, require heat for creepers, autumn for spiders, etc.")
                .define("arcticSpawning", false);
        fullMelting = builder.comment("Enable alternative snow melting algorithm that can melt full snow blocks left by other mods, requires Serene Seasons to work.")
                .define("fullMelting", false);
        alternativeTornadoStrengthGrabbing = builder.comment("Enable alternative tornado strength grabbing, limiting debug output, optimizing performance and allowing more blocks to grab.")
                .define("alternativeTornadoStrengthGrabbing", false);
        builder.build();
    }
}
