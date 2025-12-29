package com.judiraal.arcticnights.mixin.sereneseasons;

import com.judiraal.arcticnights.ArcticNightsConfig;
import com.judiraal.arcticnights.util.SnowMeltHandler;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import sereneseasons.config.SeasonsConfig;
import sereneseasons.season.RandomUpdateHandler;

@Mixin(RandomUpdateHandler.class)
public abstract class RandomUpdateHandlerMixin {
    @Shadow
    private static void meltInChunk(ChunkMap chunkMap, LevelChunk chunkIn, float meltChance) {}

    @Redirect(method = "onWorldTick", at = @At(value = "INVOKE", target = "Lsereneseasons/season/RandomUpdateHandler;meltInChunk(Lnet/minecraft/server/level/ChunkMap;Lnet/minecraft/world/level/chunk/LevelChunk;F)V"))
    private static void arcticnights$meltInChunk(ChunkMap chunkMap, LevelChunk chunkIn, float meltChance, @Local(name = "seasonProperties") SeasonsConfig.SeasonProperties seasonProperties) {
        if (ArcticNightsConfig.fullMelting.getAsBoolean())
            SnowMeltHandler.meltInChunk(chunkIn, meltChance, seasonProperties);
        else
            meltInChunk(chunkMap, chunkIn, meltChance);
    }
}