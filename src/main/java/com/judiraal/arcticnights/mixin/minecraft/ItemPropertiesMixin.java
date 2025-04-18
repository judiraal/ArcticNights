package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.util.Calculations;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = {"net.minecraft.client.renderer.item.ItemProperties$1"})
public class ItemPropertiesMixin {
    @ModifyExpressionValue(method = "unclampedCall", at = @At(value = "INVOKE", target = "net/minecraft/client/multiplayer/ClientLevel.getTimeOfDay (F)F"))
    private float arcticnights$unclampedCall(float original) {
        return Calculations.Client.seasonalTimeOfDay(original);
    }
}
