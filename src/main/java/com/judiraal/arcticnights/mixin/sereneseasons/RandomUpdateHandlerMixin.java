package com.judiraal.arcticnights.mixin.sereneseasons;

import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.judiraal.arcticnights.util.SnowMeltHandler;
import com.llamalad7.mixinextras.sugar.Local;
import glitchcore.event.TickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import sereneseasons.config.SeasonsConfig;
import sereneseasons.api.season.Season;
import sereneseasons.season.RandomUpdateHandler;

@Mixin(RandomUpdateHandler.class)
public abstract class RandomUpdateHandlerMixin {
    @Shadow
    private static void meltInChunk(ChunkMap chunkMap, LevelChunk chunkIn, float meltChance) {}

    @Shadow
    private static void adjustWeatherFrequency(Level level, Season.SubSeason subSeason) {}

    @Inject(method = "onWorldTick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void arcticnights$replaceClimateMelt(TickEvent.Level event, CallbackInfo ci) {
        if (!ArcticNightsFeatures.fullMeltingUsesClimate()) return;
        if (event.getPhase() != TickEvent.Phase.END || event.getLevel().isClientSide()) return;

        ServerLevel level = (ServerLevel) event.getLevel();
        Season.SubSeason subSeason = SnowMeltHandler.getSubSeason(level);
        adjustWeatherFrequency(level, subSeason);
        SnowMeltHandler.meltClimateInLoadedChunks(level, subSeason);
        ci.cancel();
    }

    @Redirect(method = "onWorldTick", at = @At(value = "INVOKE", target = "Lsereneseasons/season/RandomUpdateHandler;meltInChunk(Lnet/minecraft/server/level/ChunkMap;Lnet/minecraft/world/level/chunk/LevelChunk;F)V"))
    private static void arcticnights$meltInChunk(ChunkMap chunkMap, LevelChunk chunkIn, float meltChance, @Local(name = "seasonProperties") SeasonsConfig.SeasonProperties seasonProperties) {
        if (ArcticNightsFeatures.fullMeltingUsesClimate()) return;
        if (ArcticNightsFeatures.fullMelting())
            SnowMeltHandler.meltInChunk(chunkIn, meltChance, seasonProperties);
        else
            meltInChunk(chunkMap, chunkIn, meltChance);
    }
}
