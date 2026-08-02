package com.judiraal.arcticnights.compat.cold_sweat;

import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.judiraal.arcticnights.util.ClimateService;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.function.Function;

/**
 * Applies opt-in cave temperature responses after Cold Sweat has evaluated its ordinary elevation and cave-biome climate.
 * Player-local sources such as nearby blocks and water remain later modifiers and can still move the result.
 */
public class CaveTargetTempModifier extends TempModifier {
    @Override
    protected Function<Double, Double> calculate(LivingEntity entity, Temperature.Trait trait) {
        Level level = entity.level();
        if (!ArcticNightsFeatures.climateSystem()
                || trait != Temperature.Trait.WORLD
                || level.dimension() != Level.OVERWORLD) {
            return temperature -> temperature;
        }

        var biome = level.getBiome(entity.blockPosition());
        return temperature -> {
            double response = ClimateService.applyCaveTemperatureResponse(biome, temperature);
            double delta = response - temperature;
            return temperature + delta;
        };
    }
}
