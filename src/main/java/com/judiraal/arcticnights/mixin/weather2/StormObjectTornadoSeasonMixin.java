package com.judiraal.arcticnights.mixin.weather2;

import com.judiraal.arcticnights.util.TornadoSeasonPolicy;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import weather2.weathersystem.storm.StormObject;

@Mixin(StormObject.class)
public abstract class StormObjectTornadoSeasonMixin {
    @ModifyReturnValue(method = "rollDiceOnMaxIntensity", at = @At("RETURN"))
    private int arcticnights$limitNaturalLandTornadoes(int originalMaxIntensity) {
        StormObject storm = (StormObject) (Object) this;
        if (!TornadoSeasonPolicy.appliesToStorm(
                        storm.stormType == StormObject.TYPE_LAND,
                        storm.naturallySpawned,
                        storm.weatherMachineControlled)
                || !(storm.manager.getWorld() instanceof ServerLevel level)) {
            return originalMaxIntensity;
        }
        return TornadoSeasonPolicy.adjustNaturalLandMaxIntensity(
                level,
                originalMaxIntensity,
                StormObject.STATE_FORMING,
                StormObject.STATE_HAIL);
    }
}
