package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsConfig;
import it.unimi.dsi.fastutil.longs.Long2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.biome.Biome;
import sereneseasons.api.season.ISeasonState;
import sereneseasons.api.season.SeasonHelper;

import javax.annotation.Nullable;

public class ArcticSpawner {
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
    private static final int LAVA_LAKE_HORIZONTAL_RADIUS = 9;
    private static final int LAVA_LAKE_HORIZONTAL_STEP = 3;
    private static final int LAVA_LAKE_VERTICAL_RADIUS = 4;
    private static final int LAVA_LAKE_VERTICAL_STEP = 2;
    private static final int LAVA_LAKE_MIN_SAMPLED_SOURCES = 8;
    static final TagKey<Biome> COLD =
            TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_cold"));
    static final TagKey<Biome> HOT =
            TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_hot"));
    static final TagKey<Biome> WITCH_WETLANDS =
            TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(ArcticNights.MOD_ID, "witch_wetlands"));
    static final TagKey<EntityType<?>> REQUIRE_COLD =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("arcticnights", "require_cold"));
    static final TagKey<EntityType<?>> REQUIRE_HOT =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("arcticnights", "require_hot"));
    static final TagKey<EntityType<?>> REQUIRE_AUTUMN_OR_DEEP =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("arcticnights", "require_autumn_or_deep"));
    private static final Long2FloatLinkedOpenHashMap TEMPERATURE_CACHE = new Long2FloatLinkedOpenHashMap(1024, 0.25F) {
        @Override
        protected void rehash(int newSize) {
        }
    };
    static {
        TEMPERATURE_CACHE.defaultReturnValue(Float.NaN);
    }
    private static final Long2ByteLinkedOpenHashMap LAVA_LAKE_CACHE = new Long2ByteLinkedOpenHashMap(1024, 0.25F) {
        @Override
        protected void rehash(int newSize) {
        }
    };
    static {
        LAVA_LAKE_CACHE.defaultReturnValue((byte) 0);
    }
    private static long lastClearedGameTime;
    private static long lastClearedLavaGameTime;

    public static float getTemperature(ServerLevel level, @Nullable Holder<Biome> biome, BlockPos pos) {
        if (level.getGameTime() >> 11 != lastClearedGameTime) {
            lastClearedGameTime = level.getGameTime() >> 11;
            TEMPERATURE_CACHE.clear();
        }
        var p = pos.asLong(pos.getX() >> 2, pos.getY() >> 1, pos.getZ() >> 2) + ((level.getGameTime() >> 3) & 511);
        float t = TEMPERATURE_CACHE.get(p);
        if (!Float.isNaN(t)) return t;
        if (TEMPERATURE_CACHE.size() == 1024) TEMPERATURE_CACHE.removeFirstFloat();
        if (biome == null) biome = level.getBiome(pos);
        t = ClimateService.spawnTemperature(level, biome, pos);
        TEMPERATURE_CACHE.put(p, t);
        return t;
    }

    private static boolean isRainCooling(ServerLevel level, Holder<Biome> biome, BlockPos pos) {
        return ClimateService.isRainCooling(level, biome, pos);
    }

    public static float spawnFactor(EntityType<?> entityType, BlockPos pos, ServerLevel level) {
        if (entityType == EntityType.WITCH) {
            var biome = level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
            return biome.is(WITCH_WETLANDS) ? 1.0F : 0.0F;
        }
        if (entityType.is(REQUIRE_COLD)) {
            var biome = level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
            if (biome.is(HOT)) return 0.0F;
            var temp = getTemperature(level, biome, pos);
            float rainStrayFactor = 0.0F;
            if (isRainCooling(level, biome, pos)) {
                ClimateSnapshot snapshot = ClimateService.snapshot(level, biome, pos);
                if (snapshot.clearOutdoorMinecraftTemperature() > UNDEAD_COLD_THRESHOLD) {
                    rainStrayFactor = coldRainUndeadFactor(snapshot.outdoorMinecraftTemperature());
                    if (rainStrayFactor < MIN_MEANINGFUL_SPAWN_FACTOR) rainStrayFactor = 0.0F;
                }
            }
            float coldSeverity = Mth.clamp((UNDEAD_COLD_THRESHOLD - temp) / UNDEAD_COLD_RAMP, 0.0F, 1.0F);
            float factor = Mth.lerp(getCaveFactor(level, pos) / 2.0F, coldSeverity * coldSeverity * 2.2F, 1.0F);
            if (factor < MIN_MEANINGFUL_SPAWN_FACTOR) factor = 0.0F;
            return Math.max(factor, rainStrayFactor);
        } else if (entityType.is(REQUIRE_AUTUMN_OR_DEEP)) {
            var caveFactor = getCaveFactor(level, pos);
            var deepFactor = caveFactor >= 0.5F ? 1.0F : 0.0F;
            if (deepFactor > 0.0F) return deepFactor;
            var biome = level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
            ClimateSnapshot snapshot = ClimateService.snapshot(level, biome, pos);
            float factor = autumnSpiderFactor(level, snapshot);
            return factor < MIN_MEANINGFUL_SPIDER_FACTOR ? 0.0F : factor;
        } else if (entityType.is(REQUIRE_HOT)) {
            var biome = level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
            if (entityType == EntityType.CREEPER && isNearUndergroundLavaLake(level, pos)) return 1.0F;
            if (biome.is(COLD)) return 0.0F;
            ClimateSnapshot snapshot = ClimateService.snapshot(level, biome, pos);
            float factor = creeperFactor(snapshot, getCaveFactor(level, pos));
            return factor < MIN_MEANINGFUL_CREEPER_FACTOR ? 0.0F : factor;
        }
        return 1.0F;
    }

    private static float autumnSpiderFactor(ServerLevel level, ClimateSnapshot snapshot) {
        float seasonFactor = autumnProgressionFactor(level);
        if (seasonFactor <= 0.0F) return 0.0F;

        float temp = snapshot.outdoorMinecraftTemperature();
        float coolFactor = 1.0F - smoothStep(Mth.clamp((temp - 0.55F) / 0.35F, 0.0F, 1.0F));
        float weatherFactor = snapshot.rainCooling() ? 0.35F : 0.0F;
        if (snapshot.weatherState() == ClimateSnapshot.WeatherState.THUNDER) weatherFactor += 0.2F;
        float climateFactor = Mth.clamp(0.5F + coolFactor * 0.8F + weatherFactor, 0.35F, 1.65F);
        return seasonFactor * climateFactor;
    }

    private static float creeperFactor(ClimateSnapshot snapshot, float caveFactor) {
        float temp = snapshot.outdoorMinecraftTemperature();
        float heatSeverity = smoothStep((temp - CREEPER_HEAT_START) / (CREEPER_HEAT_FULL - CREEPER_HEAT_START));
        return Mth.lerp(caveFactor / 2.0F, heatSeverity * 2.0F, 1.0F);
    }

    private static float coldRainUndeadFactor(float outdoorMinecraftTemperature) {
        if (outdoorMinecraftTemperature >= RAIN_STRAY_UNDEAD_START) return 0.0F;
        float severity = (RAIN_STRAY_UNDEAD_START - outdoorMinecraftTemperature)
                / (RAIN_STRAY_UNDEAD_START - RAIN_STRAY_UNDEAD_FULL);
        return RAIN_STRAY_UNDEAD_FACTOR * smoothStep(severity);
    }

    private static float autumnProgressionFactor(ServerLevel level) {
        float day = SeasonsCompat.getYearDay(level);
        if (day < 40.0F) return 0.0F;
        if (day < 60.0F) return smoothStep((day - 40.0F) / 20.0F);
        if (day < 84.0F) return Mth.lerp(smoothStep((day - 60.0F) / 24.0F), 1.0F, 0.45F);
        if (day < 96.0F) return Mth.lerp(smoothStep((day - 84.0F) / 12.0F), 0.45F, 0.0F);
        return 0.0F;
    }

    private static float smoothStep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float getCaveFactor(ServerLevel level, BlockPos pos) {
        if (pos.getY() > 64) return 0;
        if (pos.getY() < 32) return 0.8F;
        return (float)(15-level.getBrightness(LightLayer.SKY, pos))/15F*0.8F;
    }

    private static boolean isNearUndergroundLavaLake(ServerLevel level, BlockPos pos) {
        if (pos.getY() >= 48) return false;
        if (level.getGameTime() >> 8 != lastClearedLavaGameTime) {
            lastClearedLavaGameTime = level.getGameTime() >> 8;
            LAVA_LAKE_CACHE.clear();
        }
        long key = BlockPos.asLong(pos.getX() >> 3, pos.getY() >> 3, pos.getZ() >> 3);
        byte cached = LAVA_LAKE_CACHE.get(key);
        if (cached != 0) return cached == 1;
        int lavaSources = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int currentChunkX = Integer.MIN_VALUE;
        int currentChunkZ = Integer.MIN_VALUE;
        LevelChunk currentChunk = null;
        for (int x = pos.getX() - LAVA_LAKE_HORIZONTAL_RADIUS; x <= pos.getX() + LAVA_LAKE_HORIZONTAL_RADIUS; x += LAVA_LAKE_HORIZONTAL_STEP) {
            int chunkX = x >> 4;
            for (int z = pos.getZ() - LAVA_LAKE_HORIZONTAL_RADIUS; z <= pos.getZ() + LAVA_LAKE_HORIZONTAL_RADIUS; z += LAVA_LAKE_HORIZONTAL_STEP) {
                int chunkZ = z >> 4;
                if (chunkX != currentChunkX || chunkZ != currentChunkZ) {
                    currentChunkX = chunkX;
                    currentChunkZ = chunkZ;
                    currentChunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                }
                if (currentChunk == null) continue;
                int minY = Math.max(level.getMinBuildHeight(), pos.getY() - LAVA_LAKE_VERTICAL_RADIUS);
                int maxY = Math.min(level.getMaxBuildHeight() - 1, pos.getY() + LAVA_LAKE_VERTICAL_RADIUS);
                for (int y = minY; y <= maxY; y += LAVA_LAKE_VERTICAL_STEP) {
                    cursor.set(x, y, z);
                    var fluid = currentChunk.getFluidState(cursor);
                    if (fluid.is(Fluids.LAVA) && fluid.isSource() && ++lavaSources >= LAVA_LAKE_MIN_SAMPLED_SOURCES) {
                        cacheLavaLakeResult(key, true);
                        return true;
                    }
                }
            }
        }
        cacheLavaLakeResult(key, false);
        return false;
    }

    private static void cacheLavaLakeResult(long key, boolean result) {
        if (LAVA_LAKE_CACHE.size() == 1024) LAVA_LAKE_CACHE.removeFirstByte();
        LAVA_LAKE_CACHE.put(key, result ? (byte) 1 : (byte) 2);
    }

    private static class SeasonsCompat {
        private static final CachedPerTick<Level, ISeasonState> SEASON_STATE = CachedPerTick.of(SeasonHelper::getSeasonState);

        static ISeasonState getSeason(ServerLevel level) {
            return SEASON_STATE.get(level.getGameTime() >> 3, level);
        }

        static float getYearDay(ServerLevel level) {
            if (ArcticNights.SERENE_SEASONS) return getSeason(level).getDay();
            int daysPerYear = ArcticNightsConfig.daysPerSeason.get() << 2;
            if (daysPerYear <= 0) return 32.0F;
            long day = level.getDayTime() / 24_000L;
            return Math.floorMod(day * 96L / daysPerYear, 96L);
        }
    }
}
