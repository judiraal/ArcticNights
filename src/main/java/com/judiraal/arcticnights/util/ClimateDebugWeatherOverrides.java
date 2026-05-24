package com.judiraal.arcticnights.util;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

final class ClimateDebugWeatherOverrides {
    private static final Map<MinecraftServer, Float> OFFSETS_BY_SERVER =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ClimateDebugWeatherOverrides() {
    }

    static void setCelsius(MinecraftServer server, double celsiusOffset) {
        OFFSETS_BY_SERVER.put(server, ClimateService.celsiusOffsetToMinecraftTemperatureOffset(celsiusOffset));
    }

    static void clear(MinecraftServer server) {
        OFFSETS_BY_SERVER.remove(server);
    }

    static boolean has(MinecraftServer server) {
        return OFFSETS_BY_SERVER.containsKey(server);
    }

    static float minecraftOffset(Level level) {
        MinecraftServer server = level.getServer();
        return server == null ? Float.NaN : minecraftOffset(server);
    }

    static double celsiusOffset(MinecraftServer server) {
        float offset = minecraftOffset(server);
        return Float.isNaN(offset) ? 0.0D : offset * ClimateService.COLD_SWEAT_CELSIUS_PER_MC;
    }

    private static float minecraftOffset(MinecraftServer server) {
        Float offset = OFFSETS_BY_SERVER.get(server);
        return offset == null ? Float.NaN : offset;
    }
}
