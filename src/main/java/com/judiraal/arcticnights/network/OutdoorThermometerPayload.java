package com.judiraal.arcticnights.network;

import com.judiraal.arcticnights.ArcticNights;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OutdoorThermometerPayload(boolean valid, double minecraftTemperature) implements CustomPacketPayload {
    public static final Type<OutdoorThermometerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArcticNights.MOD_ID, "outdoor_thermometer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OutdoorThermometerPayload> STREAM_CODEC = StreamCodec.of(
            OutdoorThermometerPayload::encode,
            OutdoorThermometerPayload::decode
    );

    public static OutdoorThermometerPayload clear() {
        return new OutdoorThermometerPayload(false, 0.0D);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, OutdoorThermometerPayload payload) {
        buffer.writeBoolean(payload.valid);
        if (payload.valid) {
            buffer.writeDouble(payload.minecraftTemperature);
        }
    }

    private static OutdoorThermometerPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean valid = buffer.readBoolean();
        return valid ? new OutdoorThermometerPayload(true, buffer.readDouble()) : clear();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
