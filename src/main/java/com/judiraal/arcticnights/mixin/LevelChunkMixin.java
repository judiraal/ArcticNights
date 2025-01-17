package com.judiraal.arcticnights.mixin;


import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.SkyDarkenHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LevelChunk.class)
public class LevelChunkMixin implements SkyDarkenHolder {
    @Final
    @Shadow
    Level level;

    @Unique
    private int msc$skyDarken = -1;
    @Unique
    private long msc$lastGameTime = -1;

    @Override
    public int msc$getSkyDarken() {
        if (msc$skyDarken == -1 && (level.dimensionType().hasCeiling() || !level.dimensionType().hasSkyLight() || level.dimensionType().hasFixedTime()))
            msc$skyDarken = -2;
        if (msc$skyDarken == -2) return ((LevelChunk)(Object)this).getLevel().getSkyDarken();
        long gameTime = level.getGameTime();
        if (msc$skyDarken == -1 || gameTime-msc$lastGameTime > 100) {
            msc$lastGameTime = gameTime;
            msc$skyDarken = ArcticNights.calcSeasonalSkyDarken(level, ((LevelChunk)(Object)this).getPos());
        }
        return msc$skyDarken;
    }
}
