package com.judiraal.arcticnights.mixin.iris;

import com.judiraal.arcticnights.ArcticNightsConfig;
import com.judiraal.arcticnights.util.Calculations;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.irisshaders.iris.shadows.ShadowRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShadowRenderer.class)
public class ShadowRendererMixin {
    @ModifyExpressionValue(method = "getSkyAngle", at = @At(value = "INVOKE", target = "net/minecraft/client/multiplayer/ClientLevel.getTimeOfDay (F)F"))
    private static float arcticnights$getSkyAngle(float original) {
        return Calculations.Client.seasonalTimeOfDay(original);
    }

    @ModifyExpressionValue(method = {"renderShadows", "createShadowFrustum"}, at = @At(value = "FIELD", target = "net/irisshaders/iris/shadows/ShadowRenderer.sunPathRotation : F"))
    private float arcticnights$sunPathRotation(float original) {
        return ArcticNightsConfig.shaderSunPathRotation.get() ? Calculations.Client.currentSunAngle() / (float) Math.PI * 180 : original;
    }
}
