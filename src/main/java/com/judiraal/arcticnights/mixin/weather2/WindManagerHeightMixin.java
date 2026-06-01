package com.judiraal.arcticnights.mixin.weather2;

import com.judiraal.arcticnights.util.LoadedHeightmapLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import weather2.weathersystem.WeatherManager;
import weather2.weathersystem.wind.WindManager;

@Mixin(value = WindManager.class, remap = false)
public abstract class WindManagerHeightMixin {
    @Shadow
    public WeatherManager manager;

    @Inject(method = "calculateAverageChunkHeightAround", at = @At("HEAD"), cancellable = true)
    private void arcticnights$calculateAverageChunkHeightAroundNoWait(
            BlockPos chunkPos,
            CallbackInfoReturnable<Integer> cir
    ) {
        int sampleRadius = 2;
        int chunkStride = 3;
        int samples = 0;
        int totalHeight = 0;
        Level level = this.manager.getWorld();

        // Weather2 stores chunk coordinates in a BlockPos here, then expands each sample back to block columns.
        for (int chunkOffsetX = -sampleRadius; chunkOffsetX <= sampleRadius; chunkOffsetX++) {
            for (int chunkOffsetZ = -sampleRadius; chunkOffsetZ <= sampleRadius; chunkOffsetZ++) {
                int x = ((chunkPos.getX() + chunkOffsetX * chunkStride) << 4) + 8;
                int z = ((chunkPos.getZ() + chunkOffsetZ * chunkStride) << 4) + 8;
                int y = LoadedHeightmapLookup.heightNoWait(level, Heightmap.Types.WORLD_SURFACE, x, z);
                if (y > level.getMinBuildHeight()) {
                    totalHeight += y;
                    samples++;
                }
            }
        }

        cir.setReturnValue(samples == 0 ? level.getMinBuildHeight() : totalHeight / samples);
    }
}
