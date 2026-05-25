package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClimateDebugWeatherPayload(boolean active, double celsiusOffset) implements CustomPacketPayload {
    public static final Type<ClimateDebugWeatherPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ArcticNights.MOD_ID, "climate_debug_weather"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClimateDebugWeatherPayload> STREAM_CODEC =
            StreamCodec.of(ClimateDebugWeatherPayload::encode, ClimateDebugWeatherPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, ClimateDebugWeatherPayload payload) {
        buffer.writeBoolean(payload.active);
        buffer.writeDouble(payload.celsiusOffset);
    }

    private static ClimateDebugWeatherPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ClimateDebugWeatherPayload(buffer.readBoolean(), buffer.readDouble());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
