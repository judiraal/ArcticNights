package com.judiraal.arcticnights.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class MachineCatchupBridge {
    private static final String IWCORE_MACHINE_CATCHUP = "com.judiraal.iwcore.machine.MachineCatchup";
    private static final String TICK_METHOD = "tickSupportedBlockEntities";

    private static Method tickMethod;
    private static boolean unavailable;

    private MachineCatchupBridge() {
    }

    static void tickSupportedBlockEntities(ServerLevel level, LevelChunk chunk, ClimateTickrateCatchup.TickResult result) {
        long extraTicks = Math.max(0L, result.appliedTicks() - 1L);
        if (extraTicks <= 0L) return;

        Method method = tickMethod();
        if (method == null) return;

        try {
            method.invoke(null, level, chunk, extraTicks, result.source(), result.debtTicks(), result.multiplier());
        } catch (IllegalAccessException exception) {
            unavailable = true;
            throw new IllegalStateException("IWCore machine catch-up bridge is not accessible", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException("IWCore machine catch-up failed", cause);
        }
    }

    private static Method tickMethod() {
        if (unavailable) return null;
        if (tickMethod != null) return tickMethod;

        try {
            Class<?> type = Class.forName(IWCORE_MACHINE_CATCHUP);
            tickMethod = type.getMethod(TICK_METHOD, ServerLevel.class, LevelChunk.class, long.class, String.class, long.class, int.class);
            return tickMethod;
        } catch (ClassNotFoundException ignored) {
            unavailable = true;
            return null;
        } catch (NoSuchMethodException exception) {
            unavailable = true;
            throw new IllegalStateException("IWCore machine catch-up bridge method is missing", exception);
        }
    }
}
