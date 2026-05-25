package com.judiraal.arcticnights.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;

import javax.annotation.Nullable;

public final class ClimateVisualWeatherState {
    private static final int MAX_AGE_TICKS = 40;
    private static final int MAX_DISTANCE_BLOCKS = 128;

    private static int entityId = -1;
    private static boolean active;
    private static BlockPos pos = BlockPos.ZERO;
    private static Biome.Precipitation precipitation = Biome.Precipitation.NONE;
    private static int receivedAtTick = Integer.MIN_VALUE;

    private ClimateVisualWeatherState() {
    }

    public static void apply(Entity entity, ClimateVisualWeatherPayload payload) {
        entityId = payload.entityId();
        active = payload.active();
        pos = payload.pos();
        precipitation = payload.precipitation();
        receivedAtTick = entity.tickCount;
    }

    @Nullable
    public static Biome.Precipitation precipitationAt(@Nullable Entity viewer, BlockPos queryPos) {
        if (!active || viewer == null || viewer.getId() != entityId) return null;

        int age = viewer.tickCount - receivedAtTick;
        if (age < 0 || age > MAX_AGE_TICKS) return null;
        if (queryPos.distSqr(pos) > MAX_DISTANCE_BLOCKS * MAX_DISTANCE_BLOCKS) return null;

        return precipitation;
    }
}
