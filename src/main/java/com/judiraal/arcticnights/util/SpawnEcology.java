package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;

public final class SpawnEcology {
    private static final float UNDEAD_COLD_THRESHOLD = 0.0F / 25.0F;
    private static final float RAIN_STRAY_UNDEAD_FACTOR = 0.10F;
    private static final float RAIN_STRAY_UNDEAD_START = 3.0F / 25.0F;
    private static final float RAIN_STRAY_UNDEAD_FULL = -3.0F / 25.0F;
    private static final float UNDEAD_COLD_RAMP = 0.48F;
    private static final float MIN_MEANINGFUL_SPAWN_FACTOR = 0.08F;
    private static final float MIN_MEANINGFUL_SPIDER_FACTOR = 0.20F;
    private static final float CREEPER_HEAT_START = 20.0F / 25.0F;
    private static final float CREEPER_HEAT_FULL = 37.0F / 25.0F;
    private static final float MIN_MEANINGFUL_CREEPER_FACTOR = 0.08F;

    static final TagKey<Biome> COLD =
            TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_cold"));
    static final TagKey<Biome> HOT =
            TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_hot"));
    static final TagKey<Biome> WITCH_WETLANDS =
            TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(ArcticNights.MOD_ID, "witch_wetlands"));
    static final TagKey<EntityType<?>> REQUIRE_COLD =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(ArcticNights.MOD_ID, "require_cold"));
    static final TagKey<EntityType<?>> REQUIRE_HOT =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(ArcticNights.MOD_ID, "require_hot"));
    static final TagKey<EntityType<?>> REQUIRE_AUTUMN_OR_DEEP =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(ArcticNights.MOD_ID, "require_autumn_or_deep"));

    private SpawnEcology() {
    }

    public static float undeadFactor(Holder<Biome> biome, float spawnTemperature, float dryMinecraftTemperature, float caveFactor, boolean rainCooling) {
        if (biome.is(HOT)) return 0.0F;
        float rainStrayFactor = rainCooling && dryMinecraftTemperature > UNDEAD_COLD_THRESHOLD
                ? coldRainUndeadFactor(spawnTemperature)
                : 0.0F;
        if (rainStrayFactor < MIN_MEANINGFUL_SPAWN_FACTOR) rainStrayFactor = 0.0F;
        float coldSeverity = Mth.clamp((UNDEAD_COLD_THRESHOLD - spawnTemperature) / UNDEAD_COLD_RAMP, 0.0F, 1.0F);
        float factor = Mth.lerp(caveFactor / 2.0F, coldSeverity * coldSeverity * 2.2F, 1.0F);
        if (factor < MIN_MEANINGFUL_SPAWN_FACTOR) factor = 0.0F;
        return Math.max(factor, rainStrayFactor);
    }

    public static float creeperFactor(Holder<Biome> biome, ClimateSnapshot snapshot, float caveFactor) {
        if (biome.is(COLD)) return 0.0F;
        float heatSeverity = smoothStep((snapshot.outdoorMinecraftTemperature() - CREEPER_HEAT_START) / (CREEPER_HEAT_FULL - CREEPER_HEAT_START));
        float factor = Mth.lerp(caveFactor / 2.0F, heatSeverity * 2.0F, 1.0F);
        return factor < MIN_MEANINGFUL_CREEPER_FACTOR ? 0.0F : factor;
    }

    public static float spiderFactor(float autumnProgressionFactor, ClimateSnapshot snapshot, float caveFactor) {
        float deepFactor = caveFactor >= 0.5F ? 1.0F : 0.0F;
        if (deepFactor > 0.0F) return deepFactor;
        if (autumnProgressionFactor <= 0.0F) return 0.0F;

        float temp = snapshot.outdoorMinecraftTemperature();
        float coolFactor = 1.0F - smoothStep(Mth.clamp((temp - 0.55F) / 0.35F, 0.0F, 1.0F));
        float weatherFactor = snapshot.rainCooling() ? 0.35F : 0.0F;
        if (snapshot.weatherState() == ClimateSnapshot.WeatherState.THUNDER) weatherFactor += 0.2F;
        float climateFactor = Mth.clamp(0.5F + coolFactor * 0.8F + weatherFactor, 0.35F, 1.65F);
        float factor = autumnProgressionFactor * climateFactor;
        return factor < MIN_MEANINGFUL_SPIDER_FACTOR ? 0.0F : factor;
    }

    public static float autumnProgressionFactor(float yearDay) {
        if (yearDay < 40.0F) return 0.0F;
        if (yearDay < 60.0F) return smoothStep((yearDay - 40.0F) / 20.0F);
        if (yearDay < 84.0F) return Mth.lerp(smoothStep((yearDay - 60.0F) / 24.0F), 1.0F, 0.45F);
        if (yearDay < 96.0F) return Mth.lerp(smoothStep((yearDay - 84.0F) / 12.0F), 0.45F, 0.0F);
        return 0.0F;
    }

    public static float autumnProgressionFactorForSubSeason(int subSeasonOrdinal) {
        return autumnProgressionFactor(subSeasonOrdinal * 8.0F + 4.0F);
    }

    public static float spawnFactor(EntityType<?> type, Holder<Biome> biome, float spawnTemperature, ClimateSnapshot snapshot, float autumnProgressionFactor, float caveFactor) {
        if (type == EntityType.WITCH) return biome.is(WITCH_WETLANDS) ? 1.0F : 0.0F;
        if (type.is(REQUIRE_COLD)) return undeadFactor(biome, spawnTemperature, snapshot.clearOutdoorMinecraftTemperature(), caveFactor, snapshot.rainCooling());
        if (type.is(REQUIRE_HOT)) return creeperFactor(biome, snapshot, caveFactor);
        if (type.is(REQUIRE_AUTUMN_OR_DEEP)) return spiderFactor(autumnProgressionFactor, snapshot, caveFactor);
        return 1.0F;
    }

    private static float coldRainUndeadFactor(float outdoorMinecraftTemperature) {
        if (outdoorMinecraftTemperature >= RAIN_STRAY_UNDEAD_START) return 0.0F;
        float severity = (RAIN_STRAY_UNDEAD_START - outdoorMinecraftTemperature)
                / (RAIN_STRAY_UNDEAD_START - RAIN_STRAY_UNDEAD_FULL);
        return RAIN_STRAY_UNDEAD_FACTOR * smoothStep(severity);
    }

    private static float smoothStep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }
}
