package com.judiraal.arcticnights.mixin.weather2;

import com.judiraal.arcticnights.util.ClimateService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import weather2.ClientTickHandler;
import weather2.ClientWeatherHelper;
import weather2.client.SceneEnhancer;

@Mixin(value = SceneEnhancer.class, remap = false)
public class SceneEnhancerMixin {
    @Redirect(method = "renderTick", at = @At(value = "INVOKE", target = "Lweather2/ClientWeatherHelper;controlVisuals(Z)V"))
    private static void arcticnights$letVanillaOwnOvercastVisuals(ClientWeatherHelper helper, boolean particlePrecipitation) {
        var clientConfig = ClientTickHandler.clientConfigData;
        if (clientConfig != null && clientConfig.overcastMode) {
            return;
        }

        helper.controlVisuals(particlePrecipitation);
    }

    @Inject(method = "shouldRainHere", at = @At("HEAD"), cancellable = true)
    private static void arcticnights$shouldRainHere(Level level, Biome biome, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(ClimateService.vanillaPrecipitationAt(level, level.getBiome(pos), pos) == Biome.Precipitation.RAIN);
    }

    @Inject(method = "shouldSnowHere", at = @At("HEAD"), cancellable = true)
    private static void arcticnights$shouldSnowHere(Level level, Biome biome, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(ClimateService.vanillaPrecipitationAt(level, level.getBiome(pos), pos) == Biome.Precipitation.SNOW);
    }
}
