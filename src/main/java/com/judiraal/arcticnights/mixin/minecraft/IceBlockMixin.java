package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.judiraal.arcticnights.util.ClimateService;
import com.judiraal.arcticnights.util.SnowMeltHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IceBlock.class, priority = 2000)
public class IceBlockMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void arcticnights$ownIceRandomTickWhenClimateActive(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!ArcticNightsFeatures.fullMeltingUsesClimate()) return;

        // Vanilla ice only melts from block light; climate-owned ice should answer outdoor history like snow.
        if (ClimateService.isExposedToSky(level, pos) && SnowMeltHandler.shouldMeltAt(level, pos)) {
            SnowMeltHandler.doMelt(state, level, pos);
        }
        ci.cancel();
    }
}
