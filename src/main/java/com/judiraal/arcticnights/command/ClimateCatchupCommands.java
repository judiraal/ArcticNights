package com.judiraal.arcticnights.command;

import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.judiraal.arcticnights.util.ClimateCatchupQueue;
import com.judiraal.arcticnights.util.ClimateSnapshot;
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

    private ClimateCatchupCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("arcticnights")
                .requires(source -> source.hasPermission(2) && ArcticNightsFeatures.debugCommands())
                .then(Commands.literal("climate")
                        .then(Commands.literal("catchup")
                                .then(Commands.literal("queue")
                                        .executes(ClimateCatchupCommands::queue))
                                .then(Commands.literal("inspect")
                                        .executes(context -> inspect(context, BlockPos.containing(context.getSource().getPosition())))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> inspect(context, BlockPosArgument.getBlockPos(context, "pos")))))
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
                                                        .executes(context -> reset(context, BlockPosArgument.getBlockPos(context, "pos"), IntegerArgumentType.getInteger(context, "radius")))))))));
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
                        + ", ops=" + value.operations())
                .orElse("last=none");
        context.getSource().sendSuccess(() -> Component.literal("Arctic Nights climate catch-up chunk "
                + chunkPos.x + "," + chunkPos.z
                + ": pending=" + result.pending()
                + ", " + stamp
                + ", " + last), false);
        return result.pending() ? 1 : 0;
    }

    private static int run(CommandContext<CommandSourceStack> context, BlockPos blockPos, int radius) {
        if (!ArcticNightsFeatures.unloadedClimateCatchupDebug()) {
            context.getSource().sendFailure(Component.literal("Set unloadedClimateCatchup.debug=true to allow debug catch-up mutation commands."));
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
        if (!ArcticNightsFeatures.unloadedClimateCatchupDebug()) {
            context.getSource().sendFailure(Component.literal("Set unloadedClimateCatchup.debug=true to allow debug catch-up mutation commands."));
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
}
