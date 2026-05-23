package com.judiraal.arcticnights.compat.cold_sweat;

import com.judiraal.arcticnights.ArcticNights;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ColdSweatReadoutTemperaturePayload(
        int entityId,
        double worldTemperature,
        double measuredTemperature,
        double feltOnlyDelta
) implements CustomPacketPayload {
    public static final Type<ColdSweatReadoutTemperaturePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ArcticNights.MOD_ID, "cold_sweat_readout_temperature"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ColdSweatReadoutTemperaturePayload> STREAM_CODEC =
            StreamCodec.of(ColdSweatReadoutTemperaturePayload::encode, ColdSweatReadoutTemperaturePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, ColdSweatReadoutTemperaturePayload payload) {
        buffer.writeVarInt(payload.entityId);
        buffer.writeDouble(payload.worldTemperature);
        buffer.writeDouble(payload.measuredTemperature);
        buffer.writeDouble(payload.feltOnlyDelta);
    }

    private static ColdSweatReadoutTemperaturePayload decode(RegistryFriendlyByteBuf buffer) {
        return new ColdSweatReadoutTemperaturePayload(
                buffer.readVarInt(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
