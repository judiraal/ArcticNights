package com.judiraal.arcticnights.client;

import com.judiraal.arcticnights.network.OutdoorThermometerPayload;
import net.minecraft.client.Minecraft;

import java.util.OptionalDouble;

public final class ClientOutdoorThermometer {
    private static final int STALE_AFTER_TICKS = 60;

    private static boolean valid;
    private static double minecraftTemperature;
    private static int receivedAtTick;

    private ClientOutdoorThermometer() {
    }

    public static void apply(OutdoorThermometerPayload payload) {
        valid = payload.valid();
        minecraftTemperature = payload.minecraftTemperature();
        Minecraft minecraft = Minecraft.getInstance();
        receivedAtTick = minecraft.player != null ? minecraft.player.tickCount : 0;
    }

    public static OptionalDouble minecraftTemperature() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!valid || minecraft.player == null) {
            return OptionalDouble.empty();
        }
        if (minecraft.player.tickCount - receivedAtTick > STALE_AFTER_TICKS) {
            valid = false;
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(minecraftTemperature);
    }
}
