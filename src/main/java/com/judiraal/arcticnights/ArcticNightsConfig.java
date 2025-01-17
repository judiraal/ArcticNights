package com.judiraal.arcticnights;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = ArcticNights.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ArcticNightsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.ConfigValue<Integer> CIRCUMFERENCE_BLOCK_DISTANCE = BUILDER
            .comment("The distance in blocks for a full circumference")
            .defineInRange("circumferenceBlockDistance", 40000, 4000, 400000);

    private static final ModConfigSpec.BooleanValue BANDED_TEMPERATURE = BUILDER
            .comment("Whether temperature-based biome distribution should follow circumference")
            .define("bandedTemperature", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static int circumferenceChunkDistance;
    public static boolean bandedTemperature;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        circumferenceChunkDistance = CIRCUMFERENCE_BLOCK_DISTANCE.get()>>4;
        bandedTemperature = BANDED_TEMPERATURE.get();
    }
}
