package com.judiraal.arcticnights.network;

import com.judiraal.arcticnights.compat.ConditionalEventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

@ConditionalEventBusSubscriber
public final class OutdoorThermometerNetworking {
    private OutdoorThermometerNetworking() {
    }

    private static void register(RegisterPayloadHandlersEvent event, IPayloadHandler<OutdoorThermometerPayload> handler) {
        event.registrar("1")
                .playToClient(OutdoorThermometerPayload.TYPE, OutdoorThermometerPayload.STREAM_CODEC, handler);
    }

    public static void sync(ServerPlayer player, double minecraftTemperature) {
        PacketDistributor.sendToPlayer(player, new OutdoorThermometerPayload(true, minecraftTemperature));
    }

    public static void clear(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, OutdoorThermometerPayload.clear());
    }

    private static void ignoreClientPayload(OutdoorThermometerPayload payload, IPayloadContext context) {
    }

    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void registerPayloads(RegisterPayloadHandlersEvent event) {
            if (FMLEnvironment.dist == Dist.CLIENT) return;
            register(event, OutdoorThermometerNetworking::ignoreClientPayload);
        }
    }

    public static final class ClientModEvents {
        private ClientModEvents() {
        }

        @SubscribeEvent
        public static void registerPayloads(RegisterPayloadHandlersEvent event) {
            register(event, com.judiraal.arcticnights.client.ClientOutdoorThermometerPayloadHandler::handle);
        }
    }
}
