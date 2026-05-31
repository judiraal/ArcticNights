package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.judiraal.arcticnights.util.ClimateService;
import com.judiraal.arcticnights.util.ClimateTickrateCatchup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public class VanillaServerLevelPrecipitationMixin {
    @Redirect(method = "tickPrecipitation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation arcticnights$precipitationAt(Biome biome, BlockPos pos) {
        if (!ArcticNightsFeatures.climateSystem()) return biome.getPrecipitationAt(pos);
        ServerLevel level = (ServerLevel) (Object) this;
        var biomeHolder = level.getBiome(pos);
        if (ClimateTickrateCatchup.isReplayingHistoricalPrecipitation(level)) {
            float outdoorTemperature = ClimateTickrateCatchup.historicalOutdoorMinecraftTemperature(level, biomeHolder, pos, true);
            long dayTime = ClimateTickrateCatchup.historicalReplayDayTime(level);
            return ClimateService.precipitationAtAsVanillaType(level, biomeHolder, pos, outdoorTemperature, dayTime);
        }
        return ClimateService.precipitationAtAsVanillaType(level, biomeHolder, pos);
    }
}
