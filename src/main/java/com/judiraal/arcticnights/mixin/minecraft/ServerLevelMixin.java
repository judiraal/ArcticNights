package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.util.ClimateService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    @Redirect(method = "tickPrecipitation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean arcticnights$shouldFreeze(Biome biome, LevelReader levelReader, BlockPos pos) {
        ServerLevel level = (ServerLevel) (Object) this;
        return ClimateService.shouldFreeze(level, level.getBiome(pos), pos);
    }

    @Redirect(method = "tickPrecipitation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean arcticnights$shouldSnow(Biome biome, LevelReader levelReader, BlockPos pos) {
        ServerLevel level = (ServerLevel) (Object) this;
        return ClimateService.shouldSnow(level, level.getBiome(pos), pos);
    }

    @Redirect(method = "tickPrecipitation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation arcticnights$precipitationAt(Biome biome, BlockPos pos) {
        ServerLevel level = (ServerLevel) (Object) this;
        return ClimateService.vanillaPrecipitationAt(level, level.getBiome(pos), pos);
    }
}
