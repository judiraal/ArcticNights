package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.ArcticNights;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Redirect(method = "renderSky", at = @At(value = "INVOKE", target = "com/mojang/math/Axis.rotationDegrees (F)Lorg/joml/Quaternionf;", ordinal = 3))
    private Quaternionf msc_solarAngle(Axis instance, float pDegrees) {
        return ArcticNights.Client.currentSunRotation();
    }

    @ModifyExpressionValue(method = "renderSky", at = @At(value = "INVOKE", target = "net/minecraft/client/multiplayer/ClientLevel.getTimeOfDay (F)F"))
    private float msc_correctTimeOfDay(float original) {
        return ArcticNights.Client.seasonalTimeOfDay(original);
    }
}
