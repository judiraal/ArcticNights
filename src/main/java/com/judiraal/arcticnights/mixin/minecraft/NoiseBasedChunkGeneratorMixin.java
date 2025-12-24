package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsConfig;
import com.judiraal.arcticnights.worldgen.ArcticTemperatureBands;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
    @Shadow
    @Final
    private Holder<NoiseGeneratorSettings> settings;

    @Unique
    private volatile NoiseRouter arcticnights$routerWithBandedTemp;

    @Unique
    private NoiseRouter arcticnights$wrapRouterOnce(NoiseRouter base) {
        NoiseRouter cached = arcticnights$routerWithBandedTemp;
        if (cached != null) return cached;

        synchronized (this) {
            cached = arcticnights$routerWithBandedTemp;
            if (cached != null) return cached;

            DensityFunction t = base.temperature();
            if (!(t instanceof ArcticTemperatureBands)) {
                ArcticNights.LOGGER.info("ANLOG: Switching NoiseRouter");
                t = new ArcticTemperatureBands(t);
            }

            cached = new NoiseRouter(
                    base.barrierNoise(),
                    base.fluidLevelFloodednessNoise(),
                    base.fluidLevelSpreadNoise(),
                    base.lavaNoise(),
                    t, // swap
                    base.vegetation(),
                    base.continents(),
                    base.erosion(),
                    base.depth(),
                    base.ridges(),
                    base.initialDensityWithoutJaggedness(),
                    base.finalDensity(),
                    base.veinToggle(),
                    base.veinRidged(),
                    base.veinGap()
            );
            arcticnights$routerWithBandedTemp = cached;
            return cached;
        }
    }

    @ModifyArg(
            method = "doCreateBiomes",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/NoiseChunk;cachedClimateSampler(Lnet/minecraft/world/level/levelgen/NoiseRouter;Ljava/util/List;)Lnet/minecraft/world/level/biome/Climate$Sampler;"
            ),
            index = 0
    )
    private NoiseRouter arcticnights$swapTemperatureRouter(NoiseRouter router) {
        if (!ArcticNightsConfig.bandedTemperature.getAsBoolean()) return router;
        if (ArcticNightsConfig.useLegacyAlgorithm.getAsBoolean()) return router;

        // settings-key based targeting
        if (!this.settings.is(NoiseGeneratorSettings.OVERWORLD)) return router;

        return arcticnights$wrapRouterOnce(router);
    }}