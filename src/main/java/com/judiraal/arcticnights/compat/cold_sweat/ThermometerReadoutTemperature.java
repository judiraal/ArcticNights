package com.judiraal.arcticnights.compat.cold_sweat;

import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.WaterTempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public final class ThermometerReadoutTemperature {
    private static final ResourceLocation COLD_SWEAT_WATER =
            ResourceLocation.fromNamespaceAndPath("cold_sweat", "water");
    private static final ResourceLocation WEATHER2_STORM =
            ResourceLocation.fromNamespaceAndPath("weather2", "storm");
    private static final double DELTA_EPSILON = 1.0E-6D;

    private static int cachedEntityId = Integer.MIN_VALUE;
    private static int cachedTick = Integer.MIN_VALUE;
    private static double cachedFeltOnlyDelta;

    private ThermometerReadoutTemperature() {
    }

    public static double feltOnlyMinecraftDeltaOrZero(LivingEntity entity) {
        if (!ArcticNightsFeatures.climateSystem()) return 0.0D;

        int entityId = entity.getId();
        int tick = entity.tickCount;
        if (cachedEntityId == entityId && cachedTick == tick) {
            return cachedFeltOnlyDelta;
        }

        List<TempModifier> modifiers = Temperature.getModifiers(entity, Temperature.Trait.WORLD);
        double allTemperature = 0.0D;
        double measuredTemperature = 0.0D;
        boolean hasFeltOnlyModifier = false;

        for (TempModifier modifier : modifiers) {
            double nextAllTemperature = modifier.apply(Temperature.Trait.WORLD, allTemperature);
            if (!Double.isNaN(nextAllTemperature)) {
                allTemperature = nextAllTemperature;
            }

            if (isFeltOnlyWorldModifier(modifier)) {
                hasFeltOnlyModifier = true;
                continue;
            }

            double nextMeasuredTemperature = modifier.apply(Temperature.Trait.WORLD, measuredTemperature);
            if (!Double.isNaN(nextMeasuredTemperature)) {
                measuredTemperature = nextMeasuredTemperature;
            }
        }

        double delta = hasFeltOnlyModifier ? allTemperature - measuredTemperature : 0.0D;
        if (Math.abs(delta) < DELTA_EPSILON) {
            delta = 0.0D;
        }

        cachedEntityId = entityId;
        cachedTick = tick;
        cachedFeltOnlyDelta = delta;
        return delta;
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
