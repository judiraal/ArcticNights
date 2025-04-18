package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.util.Calculations;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @ModifyExpressionValue(method = {"getSkyDarken", "getSkyColor", "getCloudColor", "getStarBrightness"}, at = @At(value = "INVOKE", target = "net/minecraft/client/multiplayer/ClientLevel.getTimeOfDay (F)F"))
    private float arcticnights$correctTimeOfDay(float original) {
        return Calculations.Client.seasonalTimeOfDay(original);
    }
}
