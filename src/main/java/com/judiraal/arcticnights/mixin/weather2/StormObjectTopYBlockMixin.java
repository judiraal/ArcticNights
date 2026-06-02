package com.judiraal.arcticnights.mixin.weather2;

import com.judiraal.arcticnights.util.LoadedHeightmapLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import weather2.weathersystem.storm.StormObject;

@Mixin(StormObject.class)
public class StormObjectTopYBlockMixin {
    @Redirect(method = "calculateTopYBlock",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState arcticnights$getBlockStateNoWait(Level level, BlockPos pos) {
        return LoadedHeightmapLookup.blockStateNoWait(level, pos);
    }
}
