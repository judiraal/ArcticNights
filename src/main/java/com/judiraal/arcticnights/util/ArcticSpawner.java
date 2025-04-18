package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import sereneseasons.api.season.ISeasonState;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.season.SeasonHooks;

import javax.annotation.Nullable;

public class ArcticSpawner {
    private static final TagKey<Biome> COLD =
            TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_cold"));
    private static final TagKey<Biome> HOT =
            TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_hot"));
    private static final TagKey<EntityType<?>> REQUIRE_COLD =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("arcticnights", "require_cold"));
    private static final TagKey<EntityType<?>> REQUIRE_HOT =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("arcticnights", "require_hot"));
    private static final Long2FloatLinkedOpenHashMap TEMPERATURE_CACHE = new Long2FloatLinkedOpenHashMap(1024, 0.25F) {
        @Override
        protected void rehash(int newSize) {
        }
    };
    static {
        TEMPERATURE_CACHE.defaultReturnValue(Float.NaN);
    }
    private static long lastClearedGameTime;

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
        if (level.isRaining() && level.getBrightness(LightLayer.SKY, pos) != 0 && biome.value().hasPrecipitation()) t = (t-0.3F)*2;
        TEMPERATURE_CACHE.put(p, t);
        return t;
    }

    private static final float[] subSeasonSpiderFactor = new float[] {0, 0, 0, 0, 0, 0, 0.5F, 1.5F, 3, 2, 1, 0.5F};

    public static float spawnFactor(EntityType<?> entityType, BlockPos pos, ServerLevel level) {
        if (entityType.is(REQUIRE_COLD)) {
            var biome = level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
            if (biome.is(HOT)) return 0.0F;
            var temp = getTemperature(level, biome, pos);
            if (temp > 0.15F) return 0.0F;
            return Mth.lerp(getCaveFactor(level, pos)/2, -5F*(temp-0.1F)+1F, 1.0F);
        } else if (entityType == EntityType.SPIDER && ArcticNights.SERENE_SEASONS) {
            var basicFactor = subSeasonSpiderFactor[SeasonsCompat.getSeason(level).getSubSeason().ordinal()];
            return Mth.lerp(getCaveFactor(level, pos), basicFactor, 1.0F);
        } else if (entityType.is(REQUIRE_HOT)) {
            var biome = level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
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
