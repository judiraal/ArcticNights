package com.judiraal.arcticnights.compat.cold_sweat;

import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.judiraal.arcticnights.util.ClimateService;
import com.judiraal.arcticnights.util.ClimateSnapshot;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.function.Function;

public class OutdoorClimateTempModifier extends TempModifier {
    @Override
    protected Function<Double, Double> calculate(LivingEntity entity, Temperature.Trait trait) {
        Level level = entity.level();
        if (!ArcticNightsFeatures.climateSystem()
                || trait != Temperature.Trait.WORLD
                || level.dimension() != Level.OVERWORLD) {
            return temperature -> temperature;
        }

        ClimateSnapshot snapshot = ClimateService.snapshot(level, level.getBiome(entity.blockPosition()), entity.blockPosition());
        double targetOutdoor = snapshot.outdoorMinecraftTemperature();
        return temperature -> targetOutdoor;
    }
}
