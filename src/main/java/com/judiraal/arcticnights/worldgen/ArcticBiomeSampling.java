package com.judiraal.arcticnights.worldgen;

import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;

/**
 * Builds the runtime-only Overworld settings used to create a consistent
 * {@code RandomState}. The registry-owned settings remain unchanged.
 */
@SuppressWarnings("deprecation")
public final class ArcticBiomeSampling {
    private ArcticBiomeSampling() {
    }

    public static NoiseGeneratorSettings withBandedTemperature(NoiseGeneratorSettings settings) {
        NoiseRouter router = withBandedTemperature(settings.noiseRouter());
        if (router == settings.noiseRouter()) return settings;

        return new NoiseGeneratorSettings(
                settings.noiseSettings(),
                settings.defaultBlock(),
                settings.defaultFluid(),
                router,
                settings.surfaceRule(),
                settings.spawnTarget(),
                settings.seaLevel(),
                settings.disableMobGeneration(),
                settings.isAquifersEnabled(),
                settings.oreVeinsEnabled(),
                settings.useLegacyRandomSource()
        );
    }

    public static NoiseRouter withBandedTemperature(NoiseRouter router) {
        if (router.temperature() instanceof ArcticTemperatureBands) return router;

        return new NoiseRouter(
                router.barrierNoise(),
                router.fluidLevelFloodednessNoise(),
                router.fluidLevelSpreadNoise(),
                router.lavaNoise(),
                new ArcticTemperatureBands(router.temperature()),
                router.vegetation(),
                router.continents(),
                router.erosion(),
                router.depth(),
                router.ridges(),
                router.initialDensityWithoutJaggedness(),
                router.finalDensity(),
                router.veinToggle(),
                router.veinRidged(),
                router.veinGap()
        );
    }
}
