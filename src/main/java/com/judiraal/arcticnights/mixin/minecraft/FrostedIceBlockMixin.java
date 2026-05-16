package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.ArcticNightsConfig;
import com.judiraal.arcticnights.util.SnowMeltHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FrostedIceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FrostedIceBlock.class)
public class FrostedIceBlockMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void arcticnights$cancelWarmLightMeltInFrozenClimate(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (ArcticNightsConfig.fullMelting.getAsBoolean() && !SnowMeltHandler.shouldMeltAt(level, pos)) {
            level.scheduleTick(pos, (FrostedIceBlock) (Object) this, Mth.nextInt(random, 600, 1200));
            ci.cancel();
        }
    }
}
