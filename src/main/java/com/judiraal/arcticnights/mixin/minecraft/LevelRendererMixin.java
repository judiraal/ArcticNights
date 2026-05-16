package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.util.Calculations;
import com.judiraal.arcticnights.util.ClimateService;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.math.Axis;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Shadow
    private ClientLevel level;

    @Redirect(method = "renderSky", at = @At(value = "INVOKE", target = "com/mojang/math/Axis.rotationDegrees (F)Lorg/joml/Quaternionf;", ordinal = 3))
    private Quaternionf arcticnights$solarAngle(Axis instance, float pDegrees) {
        return Calculations.Client.currentSunRotation();
    }

    @ModifyExpressionValue(method = "renderSky", at = @At(value = "INVOKE", target = "net/minecraft/client/multiplayer/ClientLevel.getTimeOfDay (F)F"))
    private float arcticnights$correctTimeOfDay(float original) {
        return Calculations.Client.seasonalTimeOfDay(original);
    }

    @Redirect(method = "renderSnowAndRain",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation arcticnights$renderPrecipitationAt(Biome biome, BlockPos pos) {
        return ClimateService.vanillaPrecipitationAt(this.level, this.level.getBiome(pos), pos);
    }

    @Redirect(method = "tickRain",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation arcticnights$tickRainPrecipitationAt(Biome biome, BlockPos pos) {
        return ClimateService.vanillaPrecipitationAt(this.level, this.level.getBiome(pos), pos);
    }
}
