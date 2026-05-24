package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.judiraal.arcticnights.util.ClimateService;
import com.judiraal.arcticnights.util.SnowAccumulation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    @Redirect(method = "tickPrecipitation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/GameRules;getInt(Lnet/minecraft/world/level/GameRules$Key;)I"))
    private int arcticnights$snowAccumulationHeight(GameRules gameRules, GameRules.Key<GameRules.IntegerValue> key) {
        int configuredHeight = gameRules.getInt(key);
        if (!ArcticNightsFeatures.climateSystem() || key != GameRules.RULE_SNOW_ACCUMULATION_HEIGHT) return configuredHeight;
        return SnowAccumulation.weatherAccumulationHeight(configuredHeight);
    }

    @Redirect(method = "tickPrecipitation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean arcticnights$shouldFreeze(Biome biome, LevelReader levelReader, BlockPos pos) {
        if (!ArcticNightsFeatures.climateSystem()) return biome.shouldFreeze(levelReader, pos);
        ServerLevel level = (ServerLevel) (Object) this;
        return ClimateService.shouldFreeze(level, level.getBiome(pos), pos);
    }

    @Redirect(method = "tickPrecipitation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean arcticnights$shouldSnow(Biome biome, LevelReader levelReader, BlockPos pos) {
        if (!ArcticNightsFeatures.climateSystem()) return biome.shouldSnow(levelReader, pos);
        ServerLevel level = (ServerLevel) (Object) this;
        return ClimateService.shouldSnow(level, level.getBiome(pos), pos);
    }

}
