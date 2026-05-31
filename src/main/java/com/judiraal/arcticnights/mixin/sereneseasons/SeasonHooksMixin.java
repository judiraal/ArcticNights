package com.judiraal.arcticnights.mixin.sereneseasons;

import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.judiraal.arcticnights.util.ClimateService;
import com.judiraal.arcticnights.util.ClimateTickrateCatchup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sereneseasons.season.SeasonHooks;

@Mixin(SeasonHooks.class)
public class SeasonHooksMixin {
    // Keep Serene's own worldgen behavior; ArcticNights only owns runtime climate decisions.
    @Inject(method = "warmEnoughToRainSeasonal(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private static void arcticnights$warmEnoughToRainSeasonal(LevelReader levelReader, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!ArcticNightsFeatures.climateSystem() || !(levelReader instanceof Level level)) return;
        cir.setReturnValue(warmEnoughToRain(level, level.getBiome(pos), pos));
    }

    @Inject(method = "warmEnoughToRainSeasonal(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/Holder;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private static void arcticnights$warmEnoughToRainSeasonal(LevelReader levelReader, Holder<Biome> biome, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!ArcticNightsFeatures.climateSystem() || !(levelReader instanceof Level level)) return;
        cir.setReturnValue(warmEnoughToRain(level, biome, pos));
    }

    @Inject(method = "coldEnoughToSnowSeasonal(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private static void arcticnights$coldEnoughToSnowSeasonal(LevelReader levelReader, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!ArcticNightsFeatures.climateSystem() || !(levelReader instanceof Level level)) return;
        cir.setReturnValue(!warmEnoughToRain(level, level.getBiome(pos), pos));
    }

    @Inject(method = "coldEnoughToSnowSeasonal(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/Holder;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private static void arcticnights$coldEnoughToSnowSeasonal(LevelReader levelReader, Holder<Biome> biome, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!ArcticNightsFeatures.climateSystem() || !(levelReader instanceof Level level)) return;
        cir.setReturnValue(!warmEnoughToRain(level, biome, pos));
    }

    private static boolean warmEnoughToRain(Level level, Holder<Biome> biome, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel && ClimateTickrateCatchup.isReplayingHistoricalClimate(serverLevel)) {
            float outdoorTemperature = ClimateTickrateCatchup.historicalOutdoorMinecraftTemperature(serverLevel, biome, pos, true);
            return ClimateService.warmEnoughToRain(outdoorTemperature);
        }
        return ClimateService.warmEnoughToRain(level, biome, pos);
    }
}
