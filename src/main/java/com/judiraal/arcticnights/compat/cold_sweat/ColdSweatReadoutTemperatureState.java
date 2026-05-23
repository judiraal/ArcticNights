package com.judiraal.arcticnights.compat.cold_sweat;

import net.minecraft.world.entity.LivingEntity;

public final class ColdSweatReadoutTemperatureState {
    private static final int MAX_SAMPLE_AGE_TICKS = 40;

    private static int entityId = Integer.MIN_VALUE;
    private static int receivedAtTick = Integer.MIN_VALUE;
    private static double worldTemperature;
    private static double measuredTemperature;
    private static double feltOnlyDelta;

    private ColdSweatReadoutTemperatureState() {
    }

    public static void apply(LivingEntity entity, ColdSweatReadoutTemperaturePayload payload) {
        entityId = payload.entityId();
        receivedAtTick = entity.tickCount;
        worldTemperature = payload.worldTemperature();
        measuredTemperature = payload.measuredTemperature();
        feltOnlyDelta = payload.feltOnlyDelta();
    }

    public static ReadoutSample lastSampleOrNull(LivingEntity entity) {
        if (entity.getId() != entityId) {
            return null;
        }

        int age = entity.tickCount - receivedAtTick;
        if (age < 0 || age > MAX_SAMPLE_AGE_TICKS) {
            return null;
        }
        return new ReadoutSample(age, worldTemperature, measuredTemperature, feltOnlyDelta);
    }

    public record ReadoutSample(int age, double worldTemperature, double measuredTemperature, double feltOnlyDelta) {
    }
}
