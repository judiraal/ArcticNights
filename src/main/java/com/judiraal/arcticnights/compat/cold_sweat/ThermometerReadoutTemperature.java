package com.judiraal.arcticnights.compat.cold_sweat;

import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.WaterTempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.OptionalDouble;

public final class ThermometerReadoutTemperature {
    private static final ResourceLocation COLD_SWEAT_WATER =
            ResourceLocation.fromNamespaceAndPath("cold_sweat", "water");
    private static final ResourceLocation WEATHER2_STORM =
            ResourceLocation.fromNamespaceAndPath("weather2", "storm");

    private ThermometerReadoutTemperature() {
    }

    public static OptionalDouble measuredMinecraftTemperature(LivingEntity entity) {
        if (!ArcticNightsFeatures.climateSystem()) return OptionalDouble.empty();

        List<TempModifier> modifiers = Temperature.getModifiers(entity, Temperature.Trait.WORLD);
        if (modifiers.stream().noneMatch(ThermometerReadoutTemperature::isFeltOnlyWorldModifier)) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(applyCachedWorldModifiers(modifiers, true));
    }

    public static double applyCachedWorldModifiers(List<TempModifier> modifiers, boolean measuredOnly) {
        double temperature = 0.0D;
        for (TempModifier modifier : modifiers) {
            if (measuredOnly && isFeltOnlyWorldModifier(modifier)) continue;
            double nextTemperature = modifier.apply(Temperature.Trait.WORLD, temperature);
            if (!Double.isNaN(nextTemperature)) {
                temperature = nextTemperature;
            }
        }
        return temperature;
    }

    public static boolean isFeltOnlyWorldModifier(TempModifier modifier) {
        ResourceLocation id = modifier.getID();
        return modifier instanceof WaterTempModifier
                || COLD_SWEAT_WATER.equals(id)
                || WEATHER2_STORM.equals(id);
    }
}
