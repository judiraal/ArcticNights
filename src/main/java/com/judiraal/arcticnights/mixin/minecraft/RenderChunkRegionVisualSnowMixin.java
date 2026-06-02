package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.client.VisualSnowPipeline;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderChunkRegion.class)
public class RenderChunkRegionVisualSnowMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void arcticnights$visualSnowCoverState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BlockState override = VisualSnowPipeline.blockStateOverride(pos);
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
