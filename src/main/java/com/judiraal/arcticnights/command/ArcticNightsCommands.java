package com.judiraal.arcticnights.command;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.compat.cold_sweat.ColdSweatDebug;
import com.judiraal.arcticnights.util.ClimateAuditReporter;
import com.judiraal.arcticnights.util.ClimateService;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.util.Locale;

public final class ArcticNightsCommands {
    private ArcticNightsCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("arcticnights")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("climate")
                        .then(Commands.literal("debug")
                                .executes(ArcticNightsCommands::debugClimate)
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ArcticNightsCommands::debugClimateAt)))
                        .then(Commands.literal("weather")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("celsius_offset", FloatArgumentType.floatArg(-25.0F, 25.0F))
                                                .executes(ArcticNightsCommands::setWeatherOffset)))
                                .then(Commands.literal("clear")
                                        .executes(ArcticNightsCommands::clearWeatherOffset)))
                        .then(Commands.literal("audit")
                                .executes(ArcticNightsCommands::auditClimate))
                        .then(Commands.literal("spawn-matrix")
                                .executes(ArcticNightsCommands::auditSpawnMatrix))));
    }

    private static int debugClimate(CommandContext<CommandSourceStack> context) {
        return debugClimateAt(context, BlockPos.containing(context.getSource().getPosition()));
    }

    private static int debugClimateAt(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return debugClimateAt(context, BlockPosArgument.getLoadedBlockPos(context, "pos"));
    }

    private static int debugClimateAt(CommandContext<CommandSourceStack> context, BlockPos pos) {
        ServerLevel level = context.getSource().getLevel();
        var biome = level.getBiome(pos);
        ResourceLocation biomeId = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome.value());
        ClimateService.ClimateBreakdown breakdown = ClimateService.breakdown(level, biome, pos);
        var snapshot = breakdown.snapshot();
        context.getSource().sendSuccess(() -> Component.literal("Arctic Nights climate at "
                + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + " (" + biomeId + "): "
                + c(snapshot.estimatedCelsius()) + " C, "
                + "mc=" + f(snapshot.outdoorMinecraftTemperature()) + ", "
                + "weather=" + snapshot.weatherState().name().toLowerCase(Locale.ROOT) + ", "
                + "precip=" + snapshot.precipitationKind().name().toLowerCase(Locale.ROOT) + ", "
                + "snow=" + snapshot.snowBehavior().name().toLowerCase(Locale.ROOT)), false);
        context.getSource().sendSuccess(() -> Component.literal("components: pole="
                + f(breakdown.poleFactor())
                + ", latitude=" + f(breakdown.latitudeMean()) + " (" + deltaC(breakdown.latitudeMean()) + " C as temp)"
                + ", seasonFactor=" + f(breakdown.seasonFactor())
                + ", seasonRange=" + f(breakdown.seasonalScale()) + " (" + deltaC(breakdown.seasonalScale()) + " C half-range)"
                + ", season=" + signedDeltaC(breakdown.seasonOffset()) + " C"
                + ", biome=" + signedDeltaC(breakdown.biomeOffset()) + " C"), false);
        context.getSource().sendSuccess(() -> Component.literal("runtime: base="
                + c(ClimateService.estimatedCelsius(breakdown.baseTemperature())) + " C"
                + ", weather=" + signedDeltaC(breakdown.weatherOffset()) + " C"
                + (ClimateService.hasDebugWeatherOffset() ? " (debug override)" : "")
                + ", day/night=" + signedDeltaC(breakdown.dayNightOffset()) + " C"
                + ", compression=" + f(breakdown.weatherCompression() * 100.0F) + "%"
                + ", clear=" + c(ClimateService.estimatedCelsius(snapshot.clearOutdoorMinecraftTemperature())) + " C"), false);
        sendColdSweatDebug(context, level, pos);
        return Math.round(snapshot.outdoorMinecraftTemperature() * 100.0F);
    }

    private static void sendColdSweatDebug(CommandContext<CommandSourceStack> context, ServerLevel level, BlockPos pos) {
        if (!ArcticNights.COLD_SWEAT) {
            context.getSource().sendSuccess(() -> Component.literal("Cold Sweat: not loaded"), false);
            return;
        }

        try {
            for (String line : ColdSweatDebug.describe(context.getSource(), level, pos)) {
                context.getSource().sendSuccess(() -> Component.literal(line), false);
            }
        } catch (Throwable t) {
            ArcticNights.LOGGER.warn("Unable to describe Cold Sweat temperature state", t);
            context.getSource().sendFailure(Component.literal("Cold Sweat debug failed: " + t.getClass().getSimpleName()
                    + ": " + t.getMessage()));
        }
    }

    private static int setWeatherOffset(CommandContext<CommandSourceStack> context) {
        float offset = FloatArgumentType.getFloat(context, "celsius_offset");
        ClimateService.setDebugWeatherOffsetCelsius(offset);
        context.getSource().sendSuccess(() -> Component.literal("Arctic Nights debug weather component set to "
                + signed(offset) + " C. Use /arcticnights climate weather clear to restore daily weather."), true);
        return Math.round(offset * 10.0F);
    }

    private static int clearWeatherOffset(CommandContext<CommandSourceStack> context) {
        ClimateService.clearDebugWeatherOffset();
        context.getSource().sendSuccess(() -> Component.literal("Arctic Nights debug weather component cleared."), true);
        return 1;
    }

    private static int auditClimate(CommandContext<CommandSourceStack> context) {
        try {
            ClimateAuditReporter.ClimateAuditResult climate = ClimateAuditReporter.audit(context.getSource().getServer());
            ClimateAuditReporter.SpawnMatrixResult spawns = ClimateAuditReporter.auditSpawnMatrix(context.getSource().getServer());
            context.getSource().sendSuccess(() -> Component.literal("Arctic Nights climate audit complete: "
                    + climate.report().biomeCount() + " biome(s), "
                    + climate.report().rowCount() + " climate row(s), "
                    + climate.report().flaggedRowCount() + " runtime flagged row(s), plus "
                    + spawns.report().rowCount() + " spawn row(s). Wrote "
                    + climate.markdown() + ", " + climate.csv() + ", "
                    + spawns.markdown() + " and " + spawns.csv()), true);
            return climate.report().flaggedRowCount();
        } catch (IOException e) {
            ArcticNights.LOGGER.warn("Unable to write Arctic Nights climate audit", e);
            context.getSource().sendFailure(Component.literal("Arctic Nights climate audit failed: " + e.getMessage()));
            return -1;
        }
    }

    private static int auditSpawnMatrix(CommandContext<CommandSourceStack> context) {
        try {
            ClimateAuditReporter.SpawnMatrixResult result = ClimateAuditReporter.auditSpawnMatrix(context.getSource().getServer());
            context.getSource().sendSuccess(() -> Component.literal("Arctic Nights spawn matrix complete: "
                    + result.report().biomeCount() + " biome(s), "
                    + result.report().rowCount() + " sample row(s), "
                    + result.report().climateModifiedRowCount() + " climate-modified row(s). Wrote "
                    + result.markdown() + " and " + result.csv()), true);
            return result.report().climateModifiedRowCount();
        } catch (IOException e) {
            ArcticNights.LOGGER.warn("Unable to write Arctic Nights spawn matrix", e);
            context.getSource().sendFailure(Component.literal("Arctic Nights spawn matrix failed: " + e.getMessage()));
            return -1;
        }
    }

    private static String f(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String c(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String deltaC(float minecraftTemperatureOffset) {
        return c(ClimateService.estimatedCelsius(minecraftTemperatureOffset));
    }

    private static String signedDeltaC(float minecraftTemperatureOffset) {
        return signed(ClimateService.estimatedCelsius(minecraftTemperatureOffset));
    }

    private static String signed(double value) {
        return String.format(Locale.ROOT, "%+.1f", value);
    }
}
