package com.judiraal.arcticnights.compat.cold_sweat;

import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
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
        List<TempModifier> modifiers = Temperature.getModifiers(living, Temperature.Trait.WORLD);
        double freshWorld = Temperature.apply(0.0D, living, Temperature.Trait.WORLD, modifiers, true);
        lines.add("Cold Sweat entity WORLD at "
                + entityPos.getX() + " " + entityPos.getY() + " " + entityPos.getZ()
                + ": cached=" + temp(cachedWorld)
                + ", fresh=" + temp(freshWorld)
                + ", modifiers=" + modifiers.size());
        lines.add("Cold Sweat WORLD modifiers: " + modifierSummary(modifiers));
        return lines;
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
}
