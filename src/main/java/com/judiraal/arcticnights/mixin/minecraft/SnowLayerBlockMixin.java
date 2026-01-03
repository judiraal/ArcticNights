package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.ArcticNightsConfig;
import com.judiraal.arcticnights.util.SnowMeltHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.SnowLayerBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SnowLayerBlock.class)
public class SnowLayerBlockMixin {
    @Redirect(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getBrightness(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I"))
    private int arcticnights$meltSnowLayer(ServerLevel level, LightLayer lightLayer, BlockPos pos) {
        final int lb = level.getBrightness(LightLayer.BLOCK, pos);
        if (lb > 11 || !ArcticNightsConfig.fullMelting.getAsBoolean()) return lb;
        final int ls = level.getBrightness(LightLayer.SKY, pos);
        if (ls <= 11 || level.random.nextInt(4) > 0) return lb;
        if (SnowMeltHandler.shouldMeltAt(level, pos)) return 12;
        return lb;
    }
}
