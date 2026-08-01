package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsConfig;
import com.judiraal.arcticnights.worldgen.ArcticBiomeSampling;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/RandomState;create(Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/core/HolderGetter;J)Lnet/minecraft/world/level/levelgen/RandomState;",
                    ordinal = 0
            ),
            index = 0
    )
    private NoiseGeneratorSettings arcticnights$wireOverworldTemperature(
            NoiseGeneratorSettings settings,
            @Local(argsOnly = true) ChunkGenerator generator
    ) {
        if (!ArcticNightsConfig.bandedTemperature.getAsBoolean()) return settings;
        if (ArcticNightsConfig.useLegacyAlgorithm.getAsBoolean()) return settings;
        if (!(generator instanceof NoiseBasedChunkGenerator noiseGenerator)) return settings;
        if (!noiseGenerator.generatorSettings().is(NoiseGeneratorSettings.OVERWORLD)) return settings;

        NoiseGeneratorSettings wrapped = ArcticBiomeSampling.withBandedTemperature(settings);
        if (wrapped != settings) {
            ArcticNights.LOGGER.debug("Wiring Arctic Nights temperature bands into the Overworld RandomState");
        }
        return wrapped;
    }
}
