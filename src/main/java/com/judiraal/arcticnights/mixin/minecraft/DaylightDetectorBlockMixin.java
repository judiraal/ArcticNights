package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.util.Calculations;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DaylightDetectorBlock.class)
public class DaylightDetectorBlockMixin {
    @Redirect(method = "useWithoutItem", at = @At(value = "INVOKE", target = "net/minecraft/world/level/block/DaylightDetectorBlock.updateSignalStrength (Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private void arcticnights$updateSignalStrength1(BlockState blockState, Level level, BlockPos pos) {
        Calculations.updateSignalStrength(blockState, level, pos);
    }

    @Redirect(method = "tickEntity", at = @At(value = "INVOKE", target = "net/minecraft/world/level/block/DaylightDetectorBlock.updateSignalStrength (Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private static void arcticnights$updateSignalStrength2(BlockState blockState, Level level, BlockPos pos) {
        Calculations.updateSignalStrength(blockState, level, pos);
    }
}
