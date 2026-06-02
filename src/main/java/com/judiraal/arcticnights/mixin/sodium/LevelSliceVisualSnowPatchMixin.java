package com.judiraal.arcticnights.mixin.sodium;

import com.judiraal.arcticnights.client.VisualSnowLevelSliceBridge;
import com.judiraal.arcticnights.client.VisualSnowPipeline;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = LevelSlice.class, remap = false)
public abstract class LevelSliceVisualSnowPatchMixin implements VisualSnowLevelSliceBridge {
    @Final
    @Shadow
    private BlockState[][] blockArrays;

    @Shadow
    private int originBlockX;

    @Shadow
    private int originBlockY;

    @Shadow
    private int originBlockZ;

    @Shadow
    public abstract BlockState getBlockState(int x, int y, int z);

    @Override
    public BlockState arcticnights$blockStateAt(int x, int y, int z) {
        return getBlockState(x, y, z);
    }

    @Override
    public BlockState arcticnights$substituteVisualSnowAt(
            VisualSnowPipeline.Substitution substitution,
            int x,
            int y,
            int z,
            BlockState originalState
    ) {
        return substitution.substituteSodiumBlock(
                (BlockAndTintGetter) (Object) this,
                blockArrays,
                originBlockX,
                originBlockY,
                originBlockZ,
                x,
                y,
                z,
                originalState
        );
    }
}
