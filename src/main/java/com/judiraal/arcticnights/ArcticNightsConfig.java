package com.judiraal.arcticnights;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

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
    public static ModConfigSpec.IntValue firstTimeOfDay;
    public static ModConfigSpec.BooleanValue shaderSunPathRotation;

    private ArcticNightsConfig(final ModConfigSpec.Builder builder) {
        circumferenceBlockDistance = builder.comment("The distance in blocks for a full circumference, i.e. double the distance between two poles")
                .defineInRange("circumferenceBlockDistance", 40000, 4000, 400000);
        bandedTemperature = builder.comment("Whether temperature-based biome distribution should follow circumference")
                .define("bandedTemperature", true);
        firstTimeOfDay = builder.comment("First time of day in game ticks when a new game is started")
                .defineInRange("firstTimeOfDay", 1000, 0, 12000);
        shaderSunPathRotation = builder.comment("Enable dynamic sunPathRotation for Iris shaders")
                .define("shaderSunPathRotation", true);
        builder.build();
    }
}
