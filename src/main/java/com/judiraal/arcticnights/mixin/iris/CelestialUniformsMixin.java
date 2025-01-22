package com.judiraal.arcticnights.mixin.iris;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.irisshaders.iris.uniforms.CelestialUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({CelestialUniforms.class})
public class CelestialUniformsMixin {
    @ModifyExpressionValue(method = "getSkyAngle", at = @At(value = "INVOKE", target = "net/minecraft/client/multiplayer/ClientLevel.getTimeOfDay (F)F"))
    private static float an$getSkyAngle(float original) {
        return ArcticNights.Client.seasonalTimeOfDay(original);
    }

    @ModifyExpressionValue(method = {"getCelestialPositionInWorldSpace", "getCelestialPosition"}, at = @At(value = "FIELD", target = "net/irisshaders/iris/uniforms/CelestialUniforms.sunPathRotation : F"))
    private float an$sunPathRotation(float original) {
        return ArcticNightsConfig.shaderSunPathRotation.get() ? ArcticNights.Client.currentSunAngle() / (float) Math.PI * 180 : original;
    }
}
