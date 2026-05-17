package com.judiraal.arcticnights.mixin.weather2;

import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.judiraal.arcticnights.util.ClimateService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import weather2.ClientWeatherProxy;
import weather2.datatypes.PrecipitationType;

@Mixin(value = ClientWeatherProxy.class, remap = false)
public class ClientWeatherProxyMixin {
    @Inject(method = "getPrecipitationType", at = @At("HEAD"), cancellable = true)
    private void arcticnights$getPrecipitationType(Biome biome, CallbackInfoReturnable<PrecipitationType> cir) {
        if (!ArcticNightsFeatures.climateSystem()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || biome == null) return;

        BlockPos pos = minecraft.player.blockPosition();
        Biome.Precipitation precipitation = ClimateService.precipitationAtAsVanillaType(minecraft.level, minecraft.level.getBiome(pos), pos);
        cir.setReturnValue(switch (precipitation) {
            case NONE -> null;
            case RAIN -> PrecipitationType.NORMAL;
            case SNOW -> PrecipitationType.SNOW;
        });
    }
}
