package com.judiraal.arcticnights.network;

import com.judiraal.arcticnights.client.ClientOutdoorThermometer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class OutdoorThermometerNetworking {
    private OutdoorThermometerNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(OutdoorThermometerPayload.TYPE, OutdoorThermometerPayload.STREAM_CODEC, OutdoorThermometerNetworking::handleClient);
    }

    public static void sync(ServerPlayer player, double minecraftTemperature) {
        PacketDistributor.sendToPlayer(player, new OutdoorThermometerPayload(true, minecraftTemperature));
    }

    public static void clear(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, OutdoorThermometerPayload.clear());
    }

    private static void handleClient(OutdoorThermometerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientOutdoorThermometer.apply(payload));
    }
}
