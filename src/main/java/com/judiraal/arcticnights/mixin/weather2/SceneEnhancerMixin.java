package com.judiraal.arcticnights.mixin.weather2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
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
}
