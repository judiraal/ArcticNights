package com.judiraal.arcticnights.compat.cold_sweat;

import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.capability.handler.EntityTempManager;
import com.momosoftworks.coldsweat.config.ConfigSettings;
import com.momosoftworks.coldsweat.util.world.WorldHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ColdSweatDebug {
    private ColdSweatDebug() {
    }

    public static List<String> describe(CommandSourceStack source, ServerLevel level, BlockPos pos) {
        List<String> lines = new ArrayList<>();
        double rawWorld = WorldHelper.getTemperatureAt(level, pos);
        lines.add("Cold Sweat raw world at debug pos: " + temp(rawWorld));

        Entity sourceEntity = source.getEntity();
        if (!(sourceEntity instanceof LivingEntity living)) {
            lines.add("Cold Sweat entity WORLD: unavailable (command source is not a living entity)");
            return lines;
        }

        BlockPos entityPos = living.blockPosition();
        double cachedWorld = Temperature.get(living, Temperature.Trait.WORLD);
        double cachedBase = Temperature.get(living, Temperature.Trait.BASE);
        double cachedCore = Temperature.get(living, Temperature.Trait.CORE);
        double cachedBody = Temperature.get(living, Temperature.Trait.BODY);
        double rate = Temperature.get(living, Temperature.Trait.RATE);
        double freezingPoint = Temperature.get(living, Temperature.Trait.FREEZING_POINT);
        double burningPoint = Temperature.get(living, Temperature.Trait.BURNING_POINT);
        List<TempModifier> modifiers = Temperature.getModifiers(living, Temperature.Trait.WORLD);
        double freshWorld = Temperature.apply(0.0D, living, Temperature.Trait.WORLD, modifiers, true);
        double measuredWorld = ThermometerReadoutTemperature.applyCachedWorldModifiers(modifiers, true);
        lines.add("Cold Sweat entity WORLD at "
                + entityPos.getX() + " " + entityPos.getY() + " " + entityPos.getZ()
                + ": cached=" + temp(cachedWorld)
                + ", fresh=" + temp(freshWorld)
                + ", modifiers=" + modifiers.size());
        lines.add("Cold Sweat thermometer readout WORLD: " + temp(measuredWorld)
                + ", felt-only delta=" + tempDelta(freshWorld - measuredWorld));
        lines.add("Cold Sweat WORLD modifiers: " + modifierSummary(modifiers));
        appendModifierEffects(lines, modifiers);
        lines.add("Cold Sweat entity BODY: body=" + bodyTemp(cachedBody)
                + ", core=" + bodyTemp(cachedCore)
                + ", base=" + bodyTemp(cachedBase));
        lines.add("Cold Sweat body gates: difficulty=" + level.getDifficulty().name().toLowerCase(Locale.ROOT)
                + ", disableInPeaceful=" + ConfigSettings.USE_PEACEFUL_MODE.get()
                + ", peacefulSuppressed=" + EntityTempManager.isPeacefulMode(living)
                + ", rate=" + bodyTemp(rate)
                + ", freezing=" + bodyTemp(freezingPoint)
                + ", burning=" + bodyTemp(burningPoint));
        lines.add("Cold Sweat BASE modifiers: " + modifierSummary(Temperature.getModifiers(living, Temperature.Trait.BASE)));
        lines.add("Cold Sweat CORE modifiers: " + modifierSummary(Temperature.getModifiers(living, Temperature.Trait.CORE)));
        return lines;
    }

    private static void appendModifierEffects(List<String> lines, List<TempModifier> modifiers) {
        if (modifiers.isEmpty()) return;

        lines.add("Cold Sweat WORLD modifier effects:");
        double temperature = 0.0D;
        int limit = Math.min(modifiers.size(), 16);
        for (int i = 0; i < limit; i++) {
            TempModifier modifier = modifiers.get(i);
            double before = temperature;
            double after = modifier.apply(Temperature.Trait.WORLD, before);
            if (Double.isNaN(after)) after = before;
            temperature = after;

            lines.add("- " + name(modifier)
                    + ": " + tempC(before)
                    + " -> " + tempC(after)
                    + " (" + tempDelta(after - before) + ")"
                    + feltOnlyLabel(modifier));
        }
        if (modifiers.size() > limit) {
            lines.add("- +" + (modifiers.size() - limit) + " more modifiers");
        }
    }

    private static String modifierSummary(List<TempModifier> modifiers) {
        if (modifiers.isEmpty()) return "none";

        StringBuilder builder = new StringBuilder();
        int limit = Math.min(modifiers.size(), 12);
        for (int i = 0; i < limit; i++) {
            if (i > 0) builder.append(" -> ");
            builder.append(name(modifiers.get(i)));
        }
        if (modifiers.size() > limit) {
            builder.append(" -> +").append(modifiers.size() - limit).append(" more");
        }
        return builder.toString();
    }

    private static String name(TempModifier modifier) {
        ResourceLocation id = modifier.getID();
        if (id != null) return id.toString();
        return modifier.getClass().getSimpleName();
    }

    private static String temp(double minecraftTemperature) {
        double celsius = Temperature.convert(
                minecraftTemperature,
                Temperature.Units.MC,
                Temperature.Units.C,
                true
        );
        return String.format(Locale.ROOT, "%.1f C, mc=%.3f", celsius, minecraftTemperature);
    }

    private static String tempC(double minecraftTemperature) {
        double celsius = Temperature.convert(
                minecraftTemperature,
                Temperature.Units.MC,
                Temperature.Units.C,
                true
        );
        return String.format(Locale.ROOT, "%.1f C", celsius);
    }

    private static String tempDelta(double minecraftTemperatureDelta) {
        double celsius = Temperature.convert(
                minecraftTemperatureDelta,
                Temperature.Units.MC,
                Temperature.Units.C,
                false
        );
        return String.format(Locale.ROOT, "%+.1f C", celsius);
    }

    private static String bodyTemp(double bodyTemperature) {
        return String.format(Locale.ROOT, "%.4f", bodyTemperature);
    }

    private static String feltOnlyLabel(TempModifier modifier) {
        return ThermometerReadoutTemperature.isFeltOnlyWorldModifier(modifier)
                ? " [felt-only; hidden from thermometer number]"
                : "";
    }
}
