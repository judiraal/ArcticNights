package com.judiraal.arcticnights.compat.cold_sweat;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.judiraal.arcticnights.compat.ConditionalEventBusSubscriber;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.client.gui.Overlays;
import com.momosoftworks.coldsweat.config.ConfigSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;
import java.util.Locale;

@ConditionalEventBusSubscriber(value = Dist.CLIENT, dependencies = {"cold_sweat"})
public final class ColdSweatClientDebug {
    private static final double EPSILON = 1.0E-6D;

    private ColdSweatClientDebug() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (!ArcticNightsFeatures.debugCommands()) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !player.isAlive() || player.tickCount % 20 != 0) return;

        double feltOnlyDelta = ThermometerReadoutTemperature.feltOnlyMinecraftDeltaOrZero(player);
        boolean advancedReadout = ColdSweatCompat.showsAdvancedWorldTemperature(player);
        if (!advancedReadout && Math.abs(feltOnlyDelta) < EPSILON) return;

        ColdSweatReadoutTemperatureState.ReadoutSample serverReadout =
                ColdSweatReadoutTemperatureState.lastSampleOrNull(player);
        double world = Temperature.get(player, Temperature.Trait.WORLD);
        double displayTarget = serverReadout == null ? world - feltOnlyDelta : serverReadout.measuredTemperature();
        double overlay = Temperature.convert(
                Overlays.WORLD_TEMP,
                ConfigSettings.UNITS.get(),
                Temperature.Units.MC,
                true
        );
        ThermometerReadoutTemperature.ReadoutCorrection correction =
                ThermometerReadoutTemperature.lastHudReadoutCorrectionOrNull(player);
        List<String> clientModifiers = Temperature.getModifiers(player, Temperature.Trait.WORLD)
                .stream()
                .map(ColdSweatClientDebug::modifierName)
                .toList();

        ArcticNights.LOGGER.info(
                "Cold Sweat client HUD readout: tick={} advanced={} units={} cap={} displayTarget={} overlay={} overlayMinusTarget={} clientFeltOnlyDelta={} serverReadout={} hook={} clientWorldModifiers={}",
                player.tickCount,
                advancedReadout,
                ConfigSettings.UNITS.get(),
                temp(world),
                temp(displayTarget),
                temp(overlay),
                delta(overlay - displayTarget),
                delta(feltOnlyDelta),
                serverReadout == null
                        ? "none"
                        : "age=" + serverReadout.age()
                        + ", cap=" + temp(serverReadout.worldTemperature())
                        + ", measured=" + temp(serverReadout.measuredTemperature())
                        + ", delta=" + delta(serverReadout.feltOnlyDelta()),
                correction == null
                        ? "not-called-this-tick"
                        : "original=" + temp(correction.original())
                        + ", delta=" + delta(correction.delta())
                        + ", corrected=" + temp(correction.corrected()),
                clientModifiers
        );
    }

    private static String modifierName(TempModifier modifier) {
        return modifier.getID() == null ? modifier.getClass().getSimpleName() : modifier.getID().toString();
    }

    private static String temp(double minecraftTemperature) {
        return String.format(
                Locale.ROOT,
                "%.1f C, mc=%.3f",
                Temperature.convert(minecraftTemperature, Temperature.Units.MC, Temperature.Units.C, true),
                minecraftTemperature
        );
    }

    private static String delta(double minecraftTemperatureDelta) {
        return String.format(
                Locale.ROOT,
                "%+.1f C, mc=%+.3f",
                Temperature.convert(minecraftTemperatureDelta, Temperature.Units.MC, Temperature.Units.C, false),
                minecraftTemperatureDelta
        );
    }
}
