package com.judiraal.arcticnights.mixin.weather2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import weather2.ClientWeatherHelper;

@Mixin(value = ClientWeatherHelper.class, remap = false)
public class ClientWeatherHelperMixin {
    @ModifyConstant(method = "controlVisuals", constant = @Constant(floatValue = 0.5F))
    private float arcticnights$restoreVanillaRainDarkening(float original) {
        return 1.0F;
    }
}
