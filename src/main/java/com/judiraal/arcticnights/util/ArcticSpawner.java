package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
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
import sereneseasons.season.SeasonHooks;

import javax.annotation.Nullable;

public class ArcticSpawner {
    private static final float UNDEAD_COLD_THRESHOLD = 0.15F;
    private static final float RAIN_STRAY_UNDEAD_FACTOR = 0.12F;
    private static final int LAVA_LAKE_HORIZONTAL_RADIUS = 9;
    private static final int LAVA_LAKE_HORIZONTAL_STEP = 3;
    private static final int LAVA_LAKE_VERTICAL_RADIUS = 4;
    private static final int LAVA_LAKE_VERTICAL_STEP = 2;
    private static final int LAVA_LAKE_MIN_SAMPLED_SOURCES = 8;
    private static final TagKey<Biome> COLD =
            TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_cold"));
    private static final TagKey<Biome> HOT =
            TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_hot"));
    private static final TagKey<EntityType<?>> REQUIRE_COLD =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("arcticnights", "require_cold"));
    private static final TagKey<EntityType<?>> REQUIRE_HOT =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("arcticnights", "require_hot"));
    private static final TagKey<EntityType<?>> REQUIRE_AUTUMN_OR_DEEP =
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
        if (ArcticNights.SERENE_SEASONS) {
            t = SeasonsCompat.getTemperature(level, biome, pos);
        } else {
            t = biome.value().getTemperature(pos);
        }
        if (isRainCooling(level, biome, pos)) t = (t-0.3F)*2;
        TEMPERATURE_CACHE.put(p, t);
        return t;
    }

    private static boolean isRainCooling(ServerLevel level, Holder<Biome> biome, BlockPos pos) {
        return level.isRaining() && level.getBrightness(LightLayer.SKY, pos) != 0 && biome.value().hasPrecipitation();
    }

    private static final float[] subSeasonSpiderFactor = new float[] {0, 0, 0, 0, 0, 0, 2, 3, 2, 1, 1, 1};

    public static float spawnFactor(EntityType<?> entityType, BlockPos pos, ServerLevel level) {
        if (entityType.is(REQUIRE_COLD)) {
            var biome = level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
            if (biome.is(HOT)) return 0.0F;
            var temp = getTemperature(level, biome, pos);
            if (temp > UNDEAD_COLD_THRESHOLD) return 0.0F;
            if (isRainCooling(level, biome, pos)) {
                float dryTemp = temp / 2.0F + 0.3F;
                if (dryTemp > UNDEAD_COLD_THRESHOLD) return RAIN_STRAY_UNDEAD_FACTOR;
            }
            return Mth.lerp(getCaveFactor(level, pos)/2, -5F*(temp-0.1F)+1F, 1.0F);
        } else if (entityType.is(REQUIRE_AUTUMN_OR_DEEP)) {
            var seasonalFactor = ArcticNights.SERENE_SEASONS
                    ? subSeasonSpiderFactor[SeasonsCompat.getSeason(level).getSubSeason().ordinal()]
                    : 0.0F;
            var deepFactor = pos.getY() < 32 ? 1.0F : 0.0F;
            return Math.max(seasonalFactor, deepFactor);
        } else if (entityType.is(REQUIRE_HOT)) {
            var biome = level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
            if (entityType == EntityType.CREEPER && isNearUndergroundLavaLake(level, pos)) return 1.0F;
            if (biome.is(COLD)) return 0.0F;
            var temp = getTemperature(level, biome, pos);
            if (temp < 0.7F) return 0.0F;
            return Mth.lerp(getCaveFactor(level, pos)/2, 3F*(temp-1F)+1F, 1.0F);
        }
        return 1.0F;
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

        static float getTemperature(ServerLevel level, Holder<Biome> biome, BlockPos pos) {
            return SeasonHooks.getBiomeTemperatureInSeason(getSeason(level).getSubSeason(), biome, pos);
        }

        static ISeasonState getSeason(ServerLevel level) {
            return SEASON_STATE.get(level.getGameTime() >> 3, level);
        }
    }
}
