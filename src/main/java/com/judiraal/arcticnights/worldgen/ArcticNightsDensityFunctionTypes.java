package com.judiraal.arcticnights.worldgen;

import com.judiraal.arcticnights.ArcticNights;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ArcticNightsDensityFunctionTypes {
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, ArcticNights.MOD_ID);

    public static final Supplier<MapCodec<? extends DensityFunction>> ARCTIC_TEMPERATURE_BANDS =
            TYPES.register("arctic_temperature_bands", () -> ArcticTemperatureBands.DATA_CODEC);
}