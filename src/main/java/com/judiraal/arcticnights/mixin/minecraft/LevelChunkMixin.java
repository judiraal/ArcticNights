package com.judiraal.arcticnights.mixin.minecraft;


import com.judiraal.arcticnights.util.SkyDarkenHolder;
import com.judiraal.arcticnights.util.Calculations;
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
    private int arcticnights$skyDarken = -1;
    @Unique
    private long arcticnights$lastGameTime = -1;

    @Override
    public int arcticnights$getSkyDarken() {
        if (arcticnights$skyDarken == -1 && (level.dimensionType().hasCeiling() || !level.dimensionType().hasSkyLight() || level.dimensionType().hasFixedTime()))
            arcticnights$skyDarken = -2;
        if (arcticnights$skyDarken == -2) return ((LevelChunk)(Object)this).getLevel().getSkyDarken();
        long gameTime = level.getGameTime();
        if (arcticnights$skyDarken == -1 || gameTime- arcticnights$lastGameTime > 100) {
            arcticnights$lastGameTime = gameTime;
            arcticnights$skyDarken = Calculations.calcSeasonalSkyDarken(level, ((LevelChunk)(Object)this).getPos());
        }
        return arcticnights$skyDarken;
    }
}
