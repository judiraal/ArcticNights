package com.judiraal.arcticnights.mixin.cold_sweat;

import com.judiraal.arcticnights.compat.cold_sweat.ThermometerReadoutTemperature;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.client.gui.Overlays;
import com.momosoftworks.coldsweat.config.ConfigSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Overlays.class, remap = false)
public abstract class WorldTemperatureReadoutMixin {
    @ModifyExpressionValue(
            method = "lambda$static$3",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/momosoftworks/coldsweat/util/math/CSMath;blend(DDDDD)D"
            ),
            require = 1
    )
    private static double arcticnights$hideFeltOnlyWorldReadoutDelta(double original) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return original;

        double delta = ThermometerReadoutTemperature.feltOnlyMinecraftDeltaOrZero(player);
        if (delta == 0.0D) return original;

        double unitDelta = Temperature.convert(
                delta,
                Temperature.Units.MC,
                ConfigSettings.UNITS.get(),
                false
        );
        return original - unitDelta;
    }
}
