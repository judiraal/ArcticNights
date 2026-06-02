package com.judiraal.arcticnights.client;

import net.minecraft.world.level.block.state.BlockState;

public interface VisualSnowLevelSliceBridge {
    BlockState arcticnights$blockStateAt(int x, int y, int z);

    BlockState arcticnights$substituteVisualSnowAt(
            VisualSnowPipeline.Substitution substitution,
            int x,
            int y,
            int z,
            BlockState originalState
    );
}
