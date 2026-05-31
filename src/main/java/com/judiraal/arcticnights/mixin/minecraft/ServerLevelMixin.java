package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.judiraal.arcticnights.util.ClimateService;
import com.judiraal.arcticnights.util.ClimateTickrateCatchup;
import com.judiraal.arcticnights.util.SeasonalGroundCover;
import com.judiraal.arcticnights.util.SnowAccumulation;
import com.judiraal.arcticnights.util.UnderTreeSnow;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
        var biomeHolder = level.getBiome(pos);
        if (ClimateTickrateCatchup.isReplayingHistoricalClimate(level)) {
            float outdoorTemperature = ClimateTickrateCatchup.historicalOutdoorMinecraftTemperature(level, biomeHolder, pos, true);
            return ClimateService.shouldFreeze(level, biomeHolder, pos, true, outdoorTemperature);
        }
        return ClimateService.shouldFreeze(level, biomeHolder, pos);
    }

    @Redirect(method = "tickPrecipitation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean arcticnights$shouldSnow(Biome biome, LevelReader levelReader, BlockPos pos) {
        if (!ArcticNightsFeatures.climateSystem()) return biome.shouldSnow(levelReader, pos);
        ServerLevel level = (ServerLevel) (Object) this;
        var biomeHolder = level.getBiome(pos);
        if (ClimateTickrateCatchup.isReplayingHistoricalPrecipitation(level)) {
            float outdoorTemperature = ClimateTickrateCatchup.historicalOutdoorMinecraftTemperature(level, biomeHolder, pos, true);
            long dayTime = ClimateTickrateCatchup.historicalReplayDayTime(level);
            return ClimateService.shouldSnow(level, biomeHolder, pos, outdoorTemperature, dayTime);
        }
        return ClimateService.shouldSnow(level, biomeHolder, pos);
    }

    @Redirect(method = "tickPrecipitation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isRaining()Z"))
    private boolean arcticnights$historicalCatchupRain(ServerLevel level) {
        if (ClimateTickrateCatchup.isReplayingHistoricalClimate(level)) {
            return ClimateTickrateCatchup.isReplayingHistoricalPrecipitation(level);
        }
        return level.isRaining();
    }

    @Redirect(method = "tickPrecipitation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z", ordinal = 2))
    private boolean arcticnights$placeSnowOverBuryableVegetation(ServerLevel level, BlockPos pos, BlockState state) {
        if (ArcticNightsFeatures.climateSystem() && SeasonalGroundCover.canBeBuriedBySnow(level.getBlockState(pos))) {
            return level.setBlock(pos, state, Block.UPDATE_ALL);
        }
        return level.setBlockAndUpdate(pos, state);
    }

    @Inject(method = "tickPrecipitation", at = @At("TAIL"))
    private void arcticnights$placeSnowUnderTrees(BlockPos blockPos, CallbackInfo ci) {
        if (!ArcticNightsFeatures.climateSystem()) return;
        ServerLevel level = (ServerLevel) (Object) this;
        UnderTreeSnow.tryPlace(level, blockPos);
    }

}
