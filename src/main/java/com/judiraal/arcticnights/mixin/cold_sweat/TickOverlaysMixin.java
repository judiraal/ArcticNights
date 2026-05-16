package com.judiraal.arcticnights.mixin.cold_sweat;

import com.judiraal.arcticnights.client.ClientOutdoorThermometer;
import com.momosoftworks.coldsweat.client.gui.Overlays;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Overlays.TickOverlays.class, remap = false)
public abstract class TickOverlaysMixin {
    @ModifyExpressionValue(
            method = "lambda$onClientTick$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/momosoftworks/coldsweat/common/capability/temperature/PlayerTempCap;getTrait(Lcom/momosoftworks/coldsweat/api/util/Temperature$Trait;)D",
                    ordinal = 0
            ),
            require = 1
    )
    private static double arcticnights$useOutdoorThermometer(double original) {
        return ClientOutdoorThermometer.minecraftTemperature().orElse(original);
    }
}
