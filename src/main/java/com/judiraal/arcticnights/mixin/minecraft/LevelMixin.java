package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.util.Calculations;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Level.class)
public class LevelMixin {
    @ModifyExpressionValue(method = "getSunAngle", at = @At(value = "INVOKE", target = "net/minecraft/world/level/Level.getTimeOfDay (F)F"))
    private float arcticnights$getSunAngle(float original) {
        if ((Object)this instanceof ClientLevel)
            return Calculations.Client.seasonalTimeOfDay(original);
        else
            return original;
    }
}
