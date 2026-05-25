package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = ArcticNights.MOD_ID)
public final class ClimateDebugWeatherNetworking {
    private ClimateDebugWeatherNetworking() {
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) return;

        ClimateDebugWeatherPayload payload = payload(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static void sync(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            PacketDistributor.sendToPlayer(player, payload(server));
        }
    }

    private static ClimateDebugWeatherPayload payload(MinecraftServer server) {
        return new ClimateDebugWeatherPayload(
                ClimateDebugWeatherOverrides.has(server),
                ClimateDebugWeatherOverrides.celsiusOffset(server)
        );
    }

    private static void handleClient(ClimateDebugWeatherPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.active()) {
                ClimateDebugWeatherOverrides.setClientCelsius(payload.celsiusOffset());
            } else {
                ClimateDebugWeatherOverrides.clearClient();
            }
        });
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                ClimateDebugWeatherPayload.TYPE,
                ClimateDebugWeatherPayload.STREAM_CODEC,
                ClimateDebugWeatherNetworking::handleClient
        );
    }
}
