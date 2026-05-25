package com.judiraal.arcticnights;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.List;

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
    public static ModConfigSpec.ConfigValue<List<? extends String>> structureDenyAllowlist;
    public static ModConfigSpec.BooleanValue arcticSpawning;
    public static ModConfigSpec.BooleanValue climateSystem;
    public static ModConfigSpec.BooleanValue debugCommands;
    public static ModConfigSpec.BooleanValue spawnDiagnostics;
    public static ModConfigSpec.BooleanValue coldSweatClientDiagnostics;
    public static ModConfigSpec.BooleanValue fullMelting;
    public static ModConfigSpec.BooleanValue alternativeTornadoStrengthGrabbing;
    public static ModConfigSpec.BooleanValue unloadedClimateCatchup;
    public static ModConfigSpec.BooleanValue unloadedClimateCatchupDebug;
    public static ModConfigSpec.BooleanValue catchupNewChunksFromWorldStart;
    public static ModConfigSpec.IntValue maxCatchupTicks;
    public static ModConfigSpec.IntValue minCatchupTicks;
    public static ModConfigSpec.IntValue catchupSampleTicks;
    public static ModConfigSpec.IntValue maxCatchupSamplesPerChunk;
    public static ModConfigSpec.IntValue maxCatchupOperationsPerChunk;
    public static ModConfigSpec.IntValue maxCatchupBlockChangesPerTick;
    public static ModConfigSpec.IntValue maxCatchupJobsPerTick;
    public static ModConfigSpec.IntValue maxCatchupQueuedChunks;
    public static ModConfigSpec.IntValue maxSnowLayersFromCatchup;
    public static ModConfigSpec.BooleanValue catchupCanCreateFullSnowBlocks;
    public static ModConfigSpec.BooleanValue catchupCanFreezeWater;
    public static ModConfigSpec.BooleanValue catchupCanMeltIce;

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
        structureDenyAllowlist = builder.comment("Structure ids allowed to generate inside structureDenyRange.",
                        "Use this for ruin-like wilderness structures while keeping villages and civilization excluded.",
                        "Supports exact ids like \"structory:graveyard\" and simple wildcard patterns like \"terralith:rubble_*\".",
                        "Default: []")
                .defineListAllowEmpty("structureDenyAllowlist", List::of, () -> "", value -> value instanceof String);
        arcticSpawning = builder.comment("Enable tag-based seasonal spawning rules, e.g. require cold for undead, require heat for creepers, autumn for spiders, etc.")
                .define("arcticSpawning", false);
        climateSystem = builder.comment("Enable Arctic Nights as the runtime outdoor climate authority for precipitation, snow/ice behavior, Weather2 visuals, and Cold Sweat outdoor WORLD temperature.",
                        "When disabled, existing vanilla/Serene Seasons/Weather2/Cold Sweat behavior is preserved.")
                .define("climateSystem", false);
        debugCommands = builder.comment("Enable /arcticnights debug and maintenance commands.",
                        "Commands are disabled by default because they only serve diagnostics.")
                .define("debugCommands", false);
        spawnDiagnostics = builder.comment("Enable detailed spawn diagnostics logging.",
                        "This can be noisy on servers and should only be enabled while investigating spawn behavior.")
                .define("spawnDiagnostics", false);
        coldSweatClientDiagnostics = builder.comment("Enable recurring Cold Sweat client HUD readout diagnostics at debug level.",
                        "This samples client temperature state and can be noisy in debug.log.")
                .define("coldSweatClientDiagnostics", false);
        fullMelting = builder.comment("Enable alternative snow melting algorithm that can melt full snow blocks left by other mods, requires Serene Seasons to work.")
                .define("fullMelting", false);
        alternativeTornadoStrengthGrabbing = builder.comment("Enable alternative tornado strength grabbing, limiting debug output, optimizing performance and allowing more blocks to grab.")
                .define("alternativeTornadoStrengthGrabbing", false);

        builder.push("unloadedClimateCatchup");
        unloadedClimateCatchup = builder.comment("Apply bounded snow, ice, freeze, and melt reconciliation when naturally loaded chunks return after being unloaded.",
                        "This is not chunk ticking; it samples climate debt when the chunk loads again.")
                .define("enabled", false);
        unloadedClimateCatchupDebug = builder.comment("Enable detailed unloaded climate catch-up logs and debug command mutation paths.")
                .define("debug", false);
        catchupNewChunksFromWorldStart = builder.comment("Apply catch-up to freshly generated chunks as if their last climate stamp was world day-time 0.",
                        "When disabled, fresh chunks are only stamped when first generated and do not receive historical snow or melt.")
                .define("newChunksFromWorldStart", false);
        maxCatchupTicks = builder.comment("Maximum climate day-time ticks a chunk may catch up from one reload.")
                .defineInRange("maxTicks", 72_000, 1_200, 1_728_000);
        minCatchupTicks = builder.comment("Minimum elapsed climate ticks before a normal chunk reload gets a catch-up job.")
                .defineInRange("minTicks", 1_200, 0, 72_000);
        catchupSampleTicks = builder.comment("Coarse sample interval used to approximate elapsed climate.")
                .defineInRange("sampleTicks", 2_400, 200, 24_000);
        maxCatchupSamplesPerChunk = builder.comment("Maximum climate samples per chunk catch-up job.")
                .defineInRange("maxSamplesPerChunk", 32, 1, 512);
        maxCatchupOperationsPerChunk = builder.comment("Maximum sampled surface operations per chunk catch-up job.")
                .defineInRange("maxOperationsPerChunk", 96, 1, 4096);
        maxCatchupBlockChangesPerTick = builder.comment("Global block-change budget for climate catch-up per server tick.")
                .defineInRange("maxBlockChangesPerTick", 64, 1, 4096);
        maxCatchupJobsPerTick = builder.comment("Maximum queued chunk catch-up jobs processed per server tick.")
                .defineInRange("maxJobsPerTick", 1, 1, 128);
        maxCatchupQueuedChunks = builder.comment("Maximum queued chunks waiting for climate catch-up. Overflowing jobs are skipped and timestamped.")
                .defineInRange("maxQueuedChunks", 512, 1, 100_000);
        maxSnowLayersFromCatchup = builder.comment("Maximum snow-layer height that unloaded catch-up may create.")
                .defineInRange("maxSnowLayers", 4, 1, 8);
        catchupCanCreateFullSnowBlocks = builder.comment("Allow catch-up to create full snow blocks. Kept off for the first slice.")
                .define("canCreateFullSnowBlocks", false);
        catchupCanFreezeWater = builder.comment("Allow catch-up to freeze exposed water when the sampled climate is cold enough.")
                .define("canFreezeWater", true);
        catchupCanMeltIce = builder.comment("Allow catch-up to melt vanilla snow and ice when the sampled climate thaws.")
                .define("canMeltIce", true);
        builder.pop();
        builder.build();
    }
}
