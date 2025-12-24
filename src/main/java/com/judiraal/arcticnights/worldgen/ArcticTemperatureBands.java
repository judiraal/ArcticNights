package com.judiraal.arcticnights.worldgen;

import com.judiraal.arcticnights.ArcticNightsConfig;
import com.judiraal.arcticnights.util.Calculations;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.NotNull;

public record ArcticTemperatureBands(DensityFunction input) implements DensityFunction {
    public static final MapCodec<ArcticTemperatureBands> DATA_CODEC =
            DensityFunction.HOLDER_HELPER_CODEC
                    .fieldOf("input")
                    .xmap(ArcticTemperatureBands::new, ArcticTemperatureBands::input);

    @Override
    public @NotNull KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return KeyDispatchDataCodec.of(ArcticNightsDensityFunctionTypes.ARCTIC_TEMPERATURE_BANDS.get());
    }

    @Override
    public double compute(@NotNull FunctionContext ctx) {
        double base = input.compute(ctx);
        if (!ArcticNightsConfig.bandedTemperature.getAsBoolean()) return base;

        int z = ctx.blockZ();
        return Calculations.seasonalClimateTemperature(z) + (base / 8.0);
    }

    @Override
    public void fillArray(double @NotNull [] array, @NotNull ContextProvider contextProvider) {
        input.fillArray(array, contextProvider);
        if (!ArcticNightsConfig.bandedTemperature.getAsBoolean()) return;

        for (int i = 0; i < array.length; i++) {
            FunctionContext ctx = contextProvider.forIndex(i);
            int z = ctx.blockZ();
            array[i] = Calculations.seasonalClimateTemperature(z) + (array[i] / 8.0);
        }
    }

    @Override
    public @NotNull DensityFunction mapAll(Visitor visitor) {
        // Make sure the inner function is also visited (important for caching/rewriting passes)
        return visitor.apply(new ArcticTemperatureBands(input.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        // Be conservative; exact bounds aren’t critical but wrong-tight bounds can break assumptions.
        return -2.0;
    }

    @Override
    public double maxValue() {
        return 2.0;
    }
}