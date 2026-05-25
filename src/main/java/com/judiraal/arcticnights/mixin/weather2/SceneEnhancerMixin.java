package com.judiraal.arcticnights.mixin.weather2;

import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.judiraal.arcticnights.util.ClimateService;
import com.judiraal.arcticnights.util.ClimateVisualWeatherState;
import extendedrenderer.particle.entity.ParticleTexFX;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import weather2.ClientTickHandler;
import weather2.ClientWeatherHelper;
import weather2.client.SceneEnhancer;

@Mixin(value = SceneEnhancer.class, remap = false)
public class SceneEnhancerMixin {
    @Redirect(method = "renderTick", at = @At(value = "INVOKE", target = "Lweather2/ClientWeatherHelper;controlVisuals(Z)V"))
    private static void arcticnights$letVanillaOwnOvercastVisuals(ClientWeatherHelper helper, boolean particlePrecipitation) {
        if (!ArcticNightsFeatures.climateSystem()) {
            helper.controlVisuals(particlePrecipitation);
            return;
        }

        var clientConfig = ClientTickHandler.clientConfigData;
        if (clientConfig != null && clientConfig.overcastMode) {
            return;
        }

        helper.controlVisuals(particlePrecipitation);
    }

    @Inject(method = "shouldRainHere", at = @At("HEAD"), cancellable = true)
    private static void arcticnights$shouldRainHere(Level level, Biome biome, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!ArcticNightsFeatures.climateSystem()) return;
        cir.setReturnValue(arcticnights$precipitationAt(level, pos) == Biome.Precipitation.RAIN);
    }

    @Inject(method = "shouldSnowHere", at = @At("HEAD"), cancellable = true)
    private static void arcticnights$shouldSnowHere(Level level, Biome biome, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!ArcticNightsFeatures.climateSystem()) return;
        cir.setReturnValue(arcticnights$precipitationAt(level, pos) == Biome.Precipitation.SNOW);
    }

    @Redirect(method = "tickEnvironmentalParticleSpawning",
            at = @At(value = "INVOKE", target = "Lextendedrenderer/particle/entity/ParticleTexFX;spawnAsWeatherEffect()V", ordinal = 0))
    private void arcticnights$spawnRainGroundSplashWhenServerRain(ParticleTexFX particle) {
        if (arcticnights$serverSaysRainAtClientPlayer()) {
            particle.spawnAsWeatherEffect();
        }
    }

    @Redirect(method = "tickEnvironmentalParticleSpawning",
            at = @At(value = "INVOKE", target = "Lextendedrenderer/particle/entity/ParticleTexFX;spawnAsWeatherEffect()V", ordinal = 1))
    private void arcticnights$spawnRainDownfallWhenServerRain(ParticleTexFX particle) {
        if (arcticnights$serverSaysRainAtClientPlayer()) {
            particle.spawnAsWeatherEffect();
        }
    }

    private static Biome.Precipitation arcticnights$precipitationAt(Level level, BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        Biome.Precipitation synced = ClimateVisualWeatherState.precipitationAt(minecraft.player, pos);
        if (synced != null) return synced;

        return ClimateService.precipitationAtAsVanillaType(level, level.getBiome(pos), pos);
    }

    private static boolean arcticnights$serverSaysRainAtClientPlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return true;
        return arcticnights$precipitationAt(minecraft.level, minecraft.player.blockPosition()) == Biome.Precipitation.RAIN;
    }
}
