package com.judiraal.arcticnights.mixin;

import com.judiraal.arcticnights.ArcticNights;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @ModifyExpressionValue(method = "setupColor", at = @At(value = "INVOKE", target = "net/minecraft/client/multiplayer/ClientLevel.getTimeOfDay (F)F"))
    private static float msc_correctTimeOfDay(float original) {
        return ArcticNights.Client.seasonalTimeOfDay(original);
    }
}
