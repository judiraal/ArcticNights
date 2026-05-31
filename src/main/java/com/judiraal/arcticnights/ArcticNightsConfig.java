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
    public static ModConfigSpec.BooleanValue climateMaintenanceCommands;
    public static ModConfigSpec.BooleanValue spawnDiagnostics;
    public static ModConfigSpec.BooleanValue coldSweatClientDiagnostics;
    public static ModConfigSpec.BooleanValue fullMelting;
    public static ModConfigSpec.BooleanValue alternativeTornadoStrengthGrabbing;
    public static ModConfigSpec.BooleanValue tickrateClimateCatchup;
    public static ModConfigSpec.BooleanValue tickrateCatchupDiagnostics;
    public static ModConfigSpec.BooleanValue tickrateCatchupNewChunksUseRecentExposure;
    public static ModConfigSpec.IntValue tickrateCatchupDebtThresholdTicks;
    public static ModConfigSpec.IntValue tickrateCatchupTargetTicks;
    public static ModConfigSpec.IntValue tickrateCatchupMaxMultiplier;
    public static ModConfigSpec.IntValue tickrateCatchupMaxBurstTicks;
    public static ModConfigSpec.IntValue tickrateCatchupMaxRandomTickDebtTicks;
    public static ModConfigSpec.IntValue tickrateCatchupMaxDebtTicks;
    public static ModConfigSpec.IntValue tickrateCatchupHistoryWindowTicks;
    public static ModConfigSpec.IntValue tickrateCatchupPrecipitationAttemptIntervalTicks;
    public static ModConfigSpec.IntValue tickrateCatchupMaxBurstChunksPerTick;

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
        fullMelting = builder.comment("Enable alternative snow melting algorithm that can melt full snow blocks left by other mods, requires Serene Seasons to work.")
                .define("fullMelting", false);
        alternativeTornadoStrengthGrabbing = builder.comment("Enable alternative tornado strength grabbing, limiting debug output, optimizing performance and allowing more blocks to grab.")
                .define("alternativeTornadoStrengthGrabbing", false);

        builder.push("commands");
        climateMaintenanceCommands = builder.comment("Register /arcticnights climate maintenance commands for snow profiling, tick-rate inspection/reset, and climate harness tests.")
                .define("climateMaintenance", false);
        builder.pop();

        builder.push("diagnostics");
        spawnDiagnostics = builder.comment("Enable detailed spawn diagnostics logging.",
                        "This can be noisy on servers and should only be enabled while investigating spawn behavior.")
                .define("spawning", false);
        coldSweatClientDiagnostics = builder.comment("Enable recurring Cold Sweat client HUD readout diagnostics at debug level.",
                        "This samples client temperature state and can be noisy in debug.log.")
                .define("coldSweatClient", false);
        tickrateCatchupDiagnostics = builder.comment("Enable detailed tick-rate climate catch-up diagnostics.")
                .define("tickrateCatchup", false);
        builder.pop();

        builder.push("tickrateClimateCatchup");
        tickrateClimateCatchup = builder.comment("Temporarily increase the random tick speed passed into vanilla per-chunk ticking when loaded chunks have missed climate ticks.",
                        "This V3 path hooks the normal chunk tick instead of running the V1 chunk-load replay queue or V2 side-loop climate mutations.")
                .define("enabled", false);
        tickrateCatchupNewChunksUseRecentExposure = builder.comment("Treat chunks without a tick-rate stamp as having recent climate catch-up debt.",
                        "Use only in controlled playtests; freshly generated chunks derive local snow/melt/random-tick debt from recorded global weather history.")
                .define("newChunksUseRecentExposure", false);
        tickrateCatchupDebtThresholdTicks = builder.comment("Ignore per-chunk climate debt at or below this many game ticks.")
                .defineInRange("debtThresholdTicks", 20, 0, 20_000);
        tickrateCatchupTargetTicks = builder.comment("Target number of vanilla chunk ticks over which a chunk should repay its climate debt.")
                .defineInRange("targetCatchupTicks", 100, 1, 20_000);
        tickrateCatchupMaxMultiplier = builder.comment("Maximum multiplier applied to vanilla random tick speed during a burst.")
                .defineInRange("maxMultiplier", 100, 1, 300);
        tickrateCatchupMaxBurstTicks = builder.comment("Maximum consecutive server ticks one chunk may spend in a catch-up burst before yielding to normal chunk ticking.",
                        "Residual debt is kept and may continue in a later burst.")
                .defineInRange("maxBurstTicks", 100, 1, 20_000);
        tickrateCatchupMaxRandomTickDebtTicks = builder.comment("Maximum random-tick catch-up ticks the tick-rate catch-up path will repay.",
                        "This includes clear/cold no-action history, so water freezing and other random ticks can catch up without requiring snow or melt history.")
                .defineInRange("maxRandomTickDebtTicks", 72_000, 20, 1_728_000);
        tickrateCatchupMaxDebtTicks = builder.comment("Maximum actionable local snow/melt replay ticks the tick-rate catch-up path will repay.",
                        "Neutral weather-history ticks do not count against this cap.")
                .defineInRange("maxDebtTicks", 72_000, 20, 1_728_000);
        tickrateCatchupHistoryWindowTicks = builder.comment("Recent global weather-history window used to derive local snow/melt/no-action exposure.")
                .defineInRange("historyWindowTicks", 72_000, 20, 1_728_000);
        tickrateCatchupPrecipitationAttemptIntervalTicks = builder.comment("Reserved for a future historical precipitation supplement.",
                        "The current V3 slice gets precipitation attempts by increasing vanilla random tick speed.")
                .defineInRange("precipitationAttemptIntervalTicks", 48, 1, 24_000);
        tickrateCatchupMaxBurstChunksPerTick = builder.comment("Target average per-server-tick budget for chunks that may receive an increased vanilla random tick speed.",
                        "The scheduler uses this target to compute a fair burst probability; short statistical spikes may exceed it, but an emergency cap still limits runaway bursts.",
                        "This does not change the per-chunk multiplier.")
                .defineInRange("maxBurstChunksPerTick", 32, 1, 512);
        builder.pop();
        builder.build();
    }
}
