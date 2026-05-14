package com.judiraal.arcticnights.command;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.util.ClimateAuditReporter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.IOException;

public final class ArcticNightsCommands {
    private ArcticNightsCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("arcticnights")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("climate")
                        .then(Commands.literal("audit")
                                .executes(ArcticNightsCommands::auditClimate))
                        .then(Commands.literal("spawn-matrix")
                                .executes(ArcticNightsCommands::auditSpawnMatrix))));
    }

    private static int auditClimate(CommandContext<CommandSourceStack> context) {
        try {
            ClimateAuditReporter.ClimateAuditResult result = ClimateAuditReporter.audit(context.getSource().getServer());
            context.getSource().sendSuccess(() -> Component.literal("Arctic Nights climate audit complete: "
                    + result.report().biomeCount() + " biome(s), "
                    + result.report().rowCount() + " sample row(s), "
                    + result.report().flaggedRowCount() + " flagged row(s). Wrote "
                    + result.markdown() + " and " + result.csv()), true);
            return result.report().flaggedRowCount();
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
}
