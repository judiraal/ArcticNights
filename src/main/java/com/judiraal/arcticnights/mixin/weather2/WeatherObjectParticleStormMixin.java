package com.judiraal.arcticnights.mixin.weather2;

import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.judiraal.arcticnights.util.ClimateService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import weather2.weathersystem.storm.WeatherObjectParticleStorm;

@Mixin(value = WeatherObjectParticleStorm.class, remap = false)
public abstract class WeatherObjectParticleStormMixin {
    @Inject(method = "isColdForStorm", at = @At("HEAD"), cancellable = true)
    private static void arcticnights$isColdForStorm(Level level, Holder<Biome> biome, boolean ignoreRiver, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!ArcticNightsFeatures.climateSystem()) return;
        cir.setReturnValue(ClimateService.hasSnowPrecipitation(level, biome, pos));
    }
}
