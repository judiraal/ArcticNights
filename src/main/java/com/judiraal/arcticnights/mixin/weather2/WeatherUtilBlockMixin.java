package com.judiraal.arcticnights.mixin.weather2;

import com.judiraal.arcticnights.util.LoadedHeightmapLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import weather2.util.WeatherUtilBlock;

@Mixin(value = WeatherUtilBlock.class, remap = false)
public class WeatherUtilBlockMixin {
    @Inject(method = "getPrecipitationHeightSafe(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/Heightmap$Types;)Lnet/minecraft/core/BlockPos;",
            at = @At("HEAD"),
            cancellable = true)
    private static void arcticnights$getPrecipitationHeightSafeNoWait(
            Level level,
            BlockPos pos,
            Heightmap.Types heightmapType,
            CallbackInfoReturnable<BlockPos> cir
    ) {
        cir.setReturnValue(LoadedHeightmapLookup.heightmapPosNoWait(level, heightmapType, pos));
    }
}
