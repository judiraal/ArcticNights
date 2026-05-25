package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public record ClimateVisualWeatherPayload(
        int entityId,
        boolean active,
        BlockPos pos,
        Biome.Precipitation precipitation
) implements CustomPacketPayload {
    public static final Type<ClimateVisualWeatherPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ArcticNights.MOD_ID, "climate_visual_weather"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClimateVisualWeatherPayload> STREAM_CODEC =
            StreamCodec.of(ClimateVisualWeatherPayload::encode, ClimateVisualWeatherPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, ClimateVisualWeatherPayload payload) {
        buffer.writeVarInt(payload.entityId);
        buffer.writeBoolean(payload.active);
        buffer.writeBlockPos(payload.pos);
        buffer.writeEnum(payload.precipitation);
    }

    private static ClimateVisualWeatherPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ClimateVisualWeatherPayload(
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBlockPos(),
                buffer.readEnum(Biome.Precipitation.class)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
