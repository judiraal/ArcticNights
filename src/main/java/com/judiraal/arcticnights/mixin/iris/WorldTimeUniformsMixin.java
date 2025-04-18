package com.judiraal.arcticnights.mixin.iris;

import com.judiraal.arcticnights.util.Calculations;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.irisshaders.iris.uniforms.WorldTimeUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldTimeUniforms.class)
public class WorldTimeUniformsMixin {
    @ModifyReturnValue(method = "getWorldDayTime", at = @At(value = "RETURN", ordinal = 1))
    private static int arcticnights$getWorldDayTime(int original) {
        return Calculations.Client.seasonalDayTime(original);
    }
}
