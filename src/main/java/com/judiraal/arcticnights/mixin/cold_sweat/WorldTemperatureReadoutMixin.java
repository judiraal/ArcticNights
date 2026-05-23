package com.judiraal.arcticnights.mixin.cold_sweat;

import com.judiraal.arcticnights.compat.cold_sweat.ColdSweatReadoutTemperatureState;
import com.judiraal.arcticnights.compat.cold_sweat.ThermometerReadoutTemperature;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.momosoftworks.coldsweat.client.gui.Overlays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Overlays.TickOverlays.class, remap = false)
public abstract class WorldTemperatureReadoutMixin {
    @ModifyExpressionValue(
            method = "lambda$onClientTick$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/momosoftworks/coldsweat/common/capability/temperature/PlayerTempCap;getTrait(Lcom/momosoftworks/coldsweat/api/util/Temperature$Trait;)D",
                    ordinal = 0
            ),
            require = 1
    )
    private static double arcticnights$hideFeltOnlyWorldReadoutDelta(double original) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return original;

        ColdSweatReadoutTemperatureState.ReadoutSample sample =
                ColdSweatReadoutTemperatureState.lastSampleOrNull(player);
        double delta = sample == null
                ? ThermometerReadoutTemperature.feltOnlyMinecraftDeltaOrZero(player)
                : original - sample.measuredTemperature();
        double corrected = original - delta;
        ThermometerReadoutTemperature.recordHudReadoutCorrection(player, original, delta, corrected);
        return corrected;
    }
}
