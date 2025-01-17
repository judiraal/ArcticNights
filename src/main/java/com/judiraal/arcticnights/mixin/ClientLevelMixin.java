package com.judiraal.arcticnights.mixin;

import com.judiraal.arcticnights.ArcticNights;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @ModifyExpressionValue(method = {"getSkyDarken", "getSkyColor", "getCloudColor", "getStarBrightness"}, at = @At(value = "INVOKE", target = "net/minecraft/client/multiplayer/ClientLevel.getTimeOfDay (F)F"))
    private float msc_correctTimeOfDay(float original) {
        return ArcticNights.Client.seasonalTimeOfDay(original);
    }
}
