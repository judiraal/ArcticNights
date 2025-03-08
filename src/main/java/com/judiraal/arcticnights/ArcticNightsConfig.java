package com.judiraal.arcticnights;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.List;

public class ArcticNightsConfig {
    public static final ArcticNightsConfig CONFIG;
    static final ForgeConfigSpec SPEC;

    static {
        Pair<ArcticNightsConfig, ForgeConfigSpec> pair =
                new ForgeConfigSpec.Builder().configure(ArcticNightsConfig::new);
        CONFIG = pair.getLeft();
        SPEC = pair.getRight();
    }

    public static ForgeConfigSpec.IntValue circumferenceBlockDistance;
    public static ForgeConfigSpec.BooleanValue bandedTemperature;
    public static ForgeConfigSpec.IntValue firstTimeOfDay;
    public static ForgeConfigSpec.BooleanValue shaderSunPathRotation;
    public static ForgeConfigSpec.IntValue daysPerSeason;
    public static ForgeConfigSpec.ConfigValue<List<? extends Integer>> structureDenyRange;

    private ArcticNightsConfig(final ForgeConfigSpec.Builder builder) {
        circumferenceBlockDistance = builder.comment("The distance in blocks for a full circumference, i.e. double the distance between two poles.")
                .defineInRange("circumferenceBlockDistance", 40000, 4000, 400000);
        bandedTemperature = builder.comment("Whether temperature-based biome distribution should follow circumference.")
                .define("bandedTemperature", true);
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

        builder.build();
    }
}
