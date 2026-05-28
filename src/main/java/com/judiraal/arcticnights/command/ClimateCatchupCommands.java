package com.judiraal.arcticnights.command;

import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.judiraal.arcticnights.util.ClimateCatchupQueue;
import com.judiraal.arcticnights.util.ClimatePlayerHarness;
import com.judiraal.arcticnights.util.ClimateSnapshot;
import com.judiraal.arcticnights.util.ClimateTickrateCatchup;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Locale;

public final class ClimateCatchupCommands {
    private static final List<String> SEED_MODES = List.of("snow", "rain", "thunder", "melt", "clear");
    private static final List<String> HARNESS_DIRECTIONS = List.of("east", "west", "south", "north");

    private ClimateCatchupCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("arcticnights")
                .requires(source -> source.hasPermission(2) && ArcticNightsFeatures.debugCommands())
                .then(Commands.literal("climate")
                        .then(Commands.literal("harness")
                                .then(Commands.literal("start")
                                        .executes(context -> startHarness(context, BlockPos.containing(context.getSource().getPosition()), "east", 16, 60))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> startHarness(context, BlockPosArgument.getBlockPos(context, "pos"), "east", 16, 60))
                                                .then(Commands.argument("direction", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(HARNESS_DIRECTIONS, builder))
                                                        .executes(context -> startHarness(context, BlockPosArgument.getBlockPos(context, "pos"), StringArgumentType.getString(context, "direction"), 16, 60))
                                                        .then(Commands.argument("chunks", IntegerArgumentType.integer(1, 64))
                                                                .executes(context -> startHarness(context, BlockPosArgument.getBlockPos(context, "pos"), StringArgumentType.getString(context, "direction"), IntegerArgumentType.getInteger(context, "chunks"), 60))
                                                                .then(Commands.argument("waitSeconds", IntegerArgumentType.integer(1, 600))
                                                                        .executes(context -> startHarness(context, BlockPosArgument.getBlockPos(context, "pos"), StringArgumentType.getString(context, "direction"), IntegerArgumentType.getInteger(context, "chunks"), IntegerArgumentType.getInteger(context, "waitSeconds"))))))))
                                .then(Commands.literal("status")
                                        .executes(ClimateCatchupCommands::harnessStatus))
                                .then(Commands.literal("stop")
                                        .executes(ClimateCatchupCommands::stopHarness)))
                        .then(Commands.literal("catchup")
                                .then(Commands.literal("queue")
                                        .executes(ClimateCatchupCommands::queue))
                                .then(Commands.literal("inspect")
                                        .executes(context -> inspect(context, BlockPos.containing(context.getSource().getPosition())))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> inspect(context, BlockPosArgument.getBlockPos(context, "pos")))))
                                .then(Commands.literal("profile")
                                        .executes(context -> profile(context, BlockPos.containing(context.getSource().getPosition())))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> profile(context, BlockPosArgument.getBlockPos(context, "pos")))))
                                .then(Commands.literal("profile-area")
                                        .executes(context -> profileArea(context, BlockPos.containing(context.getSource().getPosition()), 0))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> profileArea(context, BlockPosArgument.getBlockPos(context, "pos"), 0))
                                                .then(Commands.argument("radius", IntegerArgumentType.integer(0, 16))
                                                        .executes(context -> profileArea(context, BlockPosArgument.getBlockPos(context, "pos"), IntegerArgumentType.getInteger(context, "radius"))))))
                                .then(Commands.literal("run")
                                        .executes(context -> run(context, BlockPos.containing(context.getSource().getPosition()), 0))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> run(context, BlockPosArgument.getBlockPos(context, "pos"), 0))
                                                .then(Commands.argument("radius", IntegerArgumentType.integer(0, 8))
                                                        .executes(context -> run(context, BlockPosArgument.getBlockPos(context, "pos"), IntegerArgumentType.getInteger(context, "radius"))))))
                                .then(Commands.literal("seed")
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(SEED_MODES, builder))
                                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1_728_000))
                                                        .executes(context -> seed(context, BlockPos.containing(context.getSource().getPosition()), 0))
                                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                                .executes(context -> seed(context, BlockPosArgument.getBlockPos(context, "pos"), 0))
                                                                .then(Commands.argument("radius", IntegerArgumentType.integer(0, 8))
                                                                        .executes(context -> seed(context, BlockPosArgument.getBlockPos(context, "pos"), IntegerArgumentType.getInteger(context, "radius"))))))))
                                .then(Commands.literal("reset")
                                        .executes(context -> reset(context, BlockPos.containing(context.getSource().getPosition()), 0))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> reset(context, BlockPosArgument.getBlockPos(context, "pos"), 0))
                                                .then(Commands.argument("radius", IntegerArgumentType.integer(0, 16))
                                                        .executes(context -> reset(context, BlockPosArgument.getBlockPos(context, "pos"), IntegerArgumentType.getInteger(context, "radius")))))))
                        .then(Commands.literal("tickrate")
                                .then(Commands.literal("history")
                                        .executes(context -> tickrateHistory(context, 3_000))
                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1_728_000))
                                                .executes(context -> tickrateHistory(context, IntegerArgumentType.getInteger(context, "ticks")))))
                                .then(Commands.literal("inspect")
                                        .executes(context -> tickrateInspect(context, BlockPos.containing(context.getSource().getPosition())))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> tickrateInspect(context, BlockPosArgument.getBlockPos(context, "pos")))))
                                .then(Commands.literal("reset")
                                        .executes(context -> tickrateReset(context, BlockPos.containing(context.getSource().getPosition()), 0))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> tickrateReset(context, BlockPosArgument.getBlockPos(context, "pos"), 0))
                                                .then(Commands.argument("radius", IntegerArgumentType.integer(0, 16))
                                                        .executes(context -> tickrateReset(context, BlockPosArgument.getBlockPos(context, "pos"), IntegerArgumentType.getInteger(context, "radius")))))))));
    }

    private static int startHarness(CommandContext<CommandSourceStack> context, BlockPos origin, String directionName, int travelChunks, int waitSeconds) {
        if (!ArcticNightsFeatures.unloadedClimateCatchupDebug()) {
            context.getSource().sendFailure(Component.literal("Set unloadedClimateCatchup.debug=true to allow the climate player harness."));
            return 0;
        }

        ServerLevel level = context.getSource().getLevel();
        ClimatePlayerHarness.Direction direction = ClimatePlayerHarness.Direction.parse(directionName)
                .orElse(null);
        if (direction == null) {
            context.getSource().sendFailure(Component.literal("Unknown harness direction '" + directionName + "'. Use east, west, south, or north."));
            return 0;
        }

        ClimatePlayerHarness.StartResult result = ClimatePlayerHarness.start(level, origin, direction, travelChunks, waitSeconds);
        context.getSource().sendSuccess(() -> Component.literal("Started Arctic Nights climate player harness: " + result.summary()), true);
        return 1;
    }

    private static int harnessStatus(CommandContext<CommandSourceStack> context) {
        ClimatePlayerHarness.Status status = ClimatePlayerHarness.status();
        context.getSource().sendSuccess(() -> Component.literal("Arctic Nights climate player harness: " + status.summary()), false);
        return status.active() ? 1 : 0;
    }

    private static int stopHarness(CommandContext<CommandSourceStack> context) {
        ClimatePlayerHarness.Status status = ClimatePlayerHarness.stop("command");
        context.getSource().sendSuccess(() -> Component.literal("Stopped Arctic Nights climate player harness: " + status.summary()), true);
        return status.active() ? 1 : 0;
    }

    private static int queue(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        ClimateCatchupQueue.QueueStats stats = ClimateCatchupQueue.stats(level);
        context.getSource().sendSuccess(() -> Component.literal("Arctic Nights climate catch-up: queued="
                + stats.queued()
                + ", pending=" + stats.pending()
                + ", recent=" + stats.recentResults()
                + ", stamped=" + stats.stampedChunks()), false);
        return stats.queued();
    }

    private static int inspect(CommandContext<CommandSourceStack> context, BlockPos blockPos) {
        ServerLevel level = context.getSource().getLevel();
        ChunkPos chunkPos = new ChunkPos(blockPos);
        ClimateCatchupQueue.InspectResult result = ClimateCatchupQueue.inspect(level, chunkPos);
        long now = level.getDayTime();
        String stamp = result.stamp()
                .map(value -> "stampDay=" + value.dayTime()
                        + ", elapsed=" + Math.max(0L, now - value.dayTime())
                        + ", stampGame=" + value.gameTime())
                .orElse("stamp=none");
        String last = result.lastResult()
                .map(value -> "last=" + value.status()
                        + ", changed=" + value.changed()
                        + ", snow=" + value.snow()
                        + ", freeze=" + value.frozen()
                        + ", melt=" + value.melted()
                        + ", ops=" + value.operations()
                        + ", diagnostics=" + value.diagnostics())
                .orElse("last=none");
        context.getSource().sendSuccess(() -> Component.literal("Arctic Nights climate catch-up chunk "
                + chunkPos.x + "," + chunkPos.z
                + ": pending=" + result.pending()
                + ", " + stamp
                + ", " + last), false);
        return result.pending() ? 1 : 0;
    }

    private static int profile(CommandContext<CommandSourceStack> context, BlockPos blockPos) {
        ServerLevel level = context.getSource().getLevel();
        ChunkPos chunkPos = new ChunkPos(blockPos);
        ClimateCatchupQueue.ProfileResult result = ClimateCatchupQueue.profile(level, chunkPos);
        context.getSource().sendSuccess(() -> Component.literal("Arctic Nights climate catch-up profile chunk "
                + chunkPos.x + "," + chunkPos.z
                + ": loaded=" + result.loaded()
                + ", columns=" + result.columns()
                + ", snowEligible=" + result.snowEligible()
                + ", snowPresent=" + result.snowPresent()
                + ", snowLayerTotal=" + result.snowLayerTotal()
                + ", snowCoverage=" + ratio(result.snowPresent(), result.columns())
                + ", snowLayerAvg=" + ratio(result.snowLayerTotal(), result.columns())
                + ", snowFactorAvg=" + String.format(Locale.ROOT, "%.3f", result.averageSnowFactor())
                + ", diagnostics=" + result.diagnostics()), false);
        return result.snowEligible();
    }

    private static int profileArea(CommandContext<CommandSourceStack> context, BlockPos blockPos, int radius) {
        ServerLevel level = context.getSource().getLevel();
        ChunkPos center = new ChunkPos(blockPos);
        int chunks = 0;
        int loaded = 0;
        int columns = 0;
        int snowEligible = 0;
        int snowPresent = 0;
        int snowLayerTotal = 0;
        int minSnowPresent = Integer.MAX_VALUE;
        int maxSnowPresent = 0;
        float weightedSnowFactorTotal = 0.0F;

        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                chunks++;
                ClimateCatchupQueue.ProfileResult result = ClimateCatchupQueue.profile(level, new ChunkPos(x, z));
                if (!result.loaded()) continue;
                loaded++;
                columns += result.columns();
                snowEligible += result.snowEligible();
                snowPresent += result.snowPresent();
                snowLayerTotal += result.snowLayerTotal();
                weightedSnowFactorTotal += result.averageSnowFactor() * result.columns();
                minSnowPresent = Math.min(minSnowPresent, result.snowPresent());
                maxSnowPresent = Math.max(maxSnowPresent, result.snowPresent());
            }
        }

        if (loaded == 0) minSnowPresent = 0;
        float snowFactorAvg = columns == 0 ? 0.0F : weightedSnowFactorTotal / columns;
        String message = "Arctic Nights climate catch-up profile area center "
                + center.x + "," + center.z
                + " radius=" + radius
                + ": chunks=" + chunks
                + ", loaded=" + loaded
                + ", columns=" + columns
                + ", snowEligible=" + snowEligible
                + ", snowPresent=" + snowPresent
                + ", snowCoverage=" + ratio(snowPresent, columns)
                + ", eligibleSnowCoverage=" + ratio(snowPresent, snowEligible)
                + ", snowLayerTotal=" + snowLayerTotal
                + ", snowLayerAvg=" + ratio(snowLayerTotal, columns)
                + ", chunkSnowMin=" + minSnowPresent
                + ", chunkSnowMax=" + maxSnowPresent
                + ", snowFactorAvg=" + String.format(Locale.ROOT, "%.3f", snowFactorAvg);
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return snowPresent;
    }

    private static int run(CommandContext<CommandSourceStack> context, BlockPos blockPos, int radius) {
        if (!catchupMutationAllowed(context)) {
            return 0;
        }

        ServerLevel level = context.getSource().getLevel();
        ChunkPos chunkPos = new ChunkPos(blockPos);
        int queued = ClimateCatchupQueue.enqueueDebug(level, chunkPos, radius);
        context.getSource().sendSuccess(() -> Component.literal("Queued " + queued + " Arctic Nights climate catch-up chunk(s) around "
                + chunkPos.x + "," + chunkPos.z + " radius=" + radius + "."), true);
        return queued;
    }

    private static int seed(CommandContext<CommandSourceStack> context, BlockPos blockPos, int radius) {
        if (!catchupMutationAllowed(context)) {
            return 0;
        }

        String mode = StringArgumentType.getString(context, "mode");
        ClimateSnapshot.WeatherState weather = weatherForSeedMode(mode);
        if (weather == null) {
            context.getSource().sendFailure(Component.literal("Unknown catch-up seed mode '" + mode + "'. Use snow, rain, thunder, melt, or clear."));
            return 0;
        }

        ServerLevel level = context.getSource().getLevel();
        ChunkPos chunkPos = new ChunkPos(blockPos);
        int ticks = IntegerArgumentType.getInteger(context, "ticks");
        ClimateCatchupQueue.DebugSeedResult result = ClimateCatchupQueue.seedDebug(level, chunkPos, radius, ticks, weather);
        context.getSource().sendSuccess(() -> Component.literal("Seeded Arctic Nights catch-up history mode="
                + mode.toLowerCase(Locale.ROOT)
                + " weather=" + weather.name().toLowerCase(Locale.ROOT)
                + " fromDay=" + result.fromDayTime()
                + " toDay=" + result.toDayTime()
                + "; queued " + result.queued()
                + " chunk(s) around " + chunkPos.x + "," + chunkPos.z
                + " radius=" + radius + "."), true);
        return result.queued();
    }

    private static ClimateSnapshot.WeatherState weatherForSeedMode(String mode) {
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "snow", "rain" -> ClimateSnapshot.WeatherState.RAIN;
            case "thunder" -> ClimateSnapshot.WeatherState.THUNDER;
            case "melt", "clear" -> ClimateSnapshot.WeatherState.CLEAR;
            default -> null;
        };
    }

    private static int reset(CommandContext<CommandSourceStack> context, BlockPos blockPos, int radius) {
        ServerLevel level = context.getSource().getLevel();
        ChunkPos chunkPos = new ChunkPos(blockPos);
        int reset = ClimateCatchupQueue.reset(level, chunkPos, radius);
        context.getSource().sendSuccess(() -> Component.literal("Reset " + reset + " Arctic Nights climate catch-up record(s) around "
                + chunkPos.x + "," + chunkPos.z + " radius=" + radius + "."), true);
        return reset;
    }

    private static int tickrateInspect(CommandContext<CommandSourceStack> context, BlockPos blockPos) {
        ServerLevel level = context.getSource().getLevel();
        ChunkPos chunkPos = new ChunkPos(blockPos);
        ClimateTickrateCatchup.InspectResult result = ClimateTickrateCatchup.inspect(level, chunkPos);
        String stamp = result.stamp()
                .map(value -> "lastAppliedGame=" + value.lastAppliedGameTime()
                        + ", lastSeenGame=" + value.lastSeenGameTime()
                        + ", burstTicks=" + value.burstTicks())
                .orElse(result.loaded() ? "stamp=none" : "chunk=unloaded");
        String last = result.lastResult()
                .map(value -> "last=" + value.status()
                        + ", source=" + value.source()
                        + ", debt=" + value.debtTicks()
                        + ", multiplier=" + value.multiplier()
                        + ", baseRandomTickSpeed=" + value.baseRandomTickSpeed()
                        + ", effectiveRandomTickSpeed=" + value.effectiveRandomTickSpeed()
                        + ", appliedTicks=" + value.appliedTicks()
                        + ", window=" + value.historyWindowTicks()
                        + ", precipTicks=" + value.precipitationTicks()
                        + ", precipFraction=" + String.format(Locale.ROOT, "%.3f", value.precipitationFraction()))
                .orElse("last=none");
        context.getSource().sendSuccess(() -> Component.literal("Arctic Nights tick-rate climate catch-up chunk "
                + chunkPos.x + "," + chunkPos.z
                + ": enabled=" + ArcticNightsFeatures.tickrateClimateCatchup()
                + ", loaded=" + result.loaded()
                + ", debt=" + result.debtTicks()
                + ", activeBurstTicks=" + result.activeBurstTicks()
                + ", " + stamp
                + ", " + last), false);
        return (int) Math.min(Integer.MAX_VALUE, result.debtTicks());
    }

    private static int tickrateHistory(CommandContext<CommandSourceStack> context, int ticks) {
        ServerLevel level = context.getSource().getLevel();
        String history = ClimateTickrateCatchup.historyDebug(level, ticks);
        context.getSource().sendSuccess(() -> Component.literal("Arctic Nights tick-rate weather history: " + history), false);
        return ticks;
    }

    private static int tickrateReset(CommandContext<CommandSourceStack> context, BlockPos blockPos, int radius) {
        if (!ArcticNightsFeatures.tickrateClimateCatchupDebug()) {
            context.getSource().sendFailure(Component.literal("Set tickrateClimateCatchup.debug=true to allow tick-rate catch-up reset commands."));
            return 0;
        }

        ServerLevel level = context.getSource().getLevel();
        ChunkPos chunkPos = new ChunkPos(blockPos);
        int reset = ClimateTickrateCatchup.reset(level, chunkPos, radius);
        context.getSource().sendSuccess(() -> Component.literal("Reset " + reset + " Arctic Nights tick-rate climate catch-up record(s) around "
                + chunkPos.x + "," + chunkPos.z + " radius=" + radius + "."), true);
        return reset;
    }

    private static boolean catchupMutationAllowed(CommandContext<CommandSourceStack> context) {
        if (!ArcticNightsFeatures.unloadedClimateCatchupDebug()) {
            context.getSource().sendFailure(Component.literal("Set unloadedClimateCatchup.debug=true to allow debug catch-up mutation commands."));
            return false;
        }
        if (!ArcticNightsFeatures.unloadedClimateCatchup()) {
            context.getSource().sendFailure(Component.literal("Set unloadedClimateCatchup.enabled=true to allow v1 climate catch-up mutation commands."));
            return false;
        }
        return true;
    }

    private static String ratio(int numerator, int denominator) {
        return String.format(Locale.ROOT, "%.3f", numerator / (double) Math.max(1, denominator));
    }
}
