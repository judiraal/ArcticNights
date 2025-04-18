package com.judiraal.arcticnights.mixin.iris;

import com.judiraal.arcticnights.ArcticNightsConfig;
import com.judiraal.arcticnights.util.Calculations;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.irisshaders.iris.uniforms.CelestialUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({CelestialUniforms.class})
public class CelestialUniformsMixin {
    @ModifyExpressionValue(method = "getSkyAngle", at = @At(value = "INVOKE", target = "net/minecraft/client/multiplayer/ClientLevel.getTimeOfDay (F)F"))
    private static float arcticnights$getSkyAngle(float original) {
        return Calculations.Client.seasonalTimeOfDay(original);
    }

    @ModifyExpressionValue(method = {"getCelestialPositionInWorldSpace", "getCelestialPosition"}, at = @At(value = "FIELD", target = "net/irisshaders/iris/uniforms/CelestialUniforms.sunPathRotation : F"))
    private float arcticnights$sunPathRotation(float original) {
        return ArcticNightsConfig.shaderSunPathRotation.get() ? Calculations.Client.currentSunAngle() / (float) Math.PI * 180 : original;
    }
}
