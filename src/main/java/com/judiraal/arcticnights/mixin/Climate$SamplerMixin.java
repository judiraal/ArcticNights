package com.judiraal.arcticnights.mixin;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsConfig;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Climate.Sampler.class)
public class Climate$SamplerMixin {
    @Redirect(method = "sample", at = @At(value = "INVOKE", ordinal = 0, target = "net/minecraft/world/level/levelgen/DensityFunction.compute (Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;)D"))
    private double msc_bandedTemperature(DensityFunction instance, DensityFunction.FunctionContext functionContext, @Local(ordinal = 3) int xPos, @Local(ordinal = 5) int zPos) {
        double density = instance.compute(functionContext);
        return ArcticNightsConfig.bandedTemperature ? ArcticNights.seasonalClimateTemperature(zPos) + (density / 8) : density;
    }
}
