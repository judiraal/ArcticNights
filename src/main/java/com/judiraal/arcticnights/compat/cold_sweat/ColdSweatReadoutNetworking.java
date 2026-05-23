package com.judiraal.arcticnights.compat.cold_sweat;

import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.judiraal.arcticnights.compat.ConditionalEventBusSubscriber;
import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@ConditionalEventBusSubscriber(dependencies = {"cold_sweat"})
public final class ColdSweatReadoutNetworking {
    private static final int SYNC_INTERVAL_TICKS = 5;

    private ColdSweatReadoutNetworking() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!ArcticNightsFeatures.climateSystem() || event.getEntity().tickCount % SYNC_INTERVAL_TICKS != 0) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    private static void sync(ServerPlayer player) {
        double worldTemperature = Temperature.get(player, Temperature.Trait.WORLD);
        double feltOnlyDelta = ThermometerReadoutTemperature.feltOnlyMinecraftDeltaOrZero(player);
        double measuredTemperature = worldTemperature - feltOnlyDelta;
        PacketDistributor.sendToPlayer(player, new ColdSweatReadoutTemperaturePayload(
                player.getId(),
                worldTemperature,
                measuredTemperature,
                feltOnlyDelta
        ));
    }

    private static void handleClient(ColdSweatReadoutTemperaturePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().getEntity(payload.entityId()) instanceof LivingEntity entity) {
                ColdSweatReadoutTemperatureState.apply(entity, payload);
            }
        });
    }

    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(
                    ColdSweatReadoutTemperaturePayload.TYPE,
                    ColdSweatReadoutTemperaturePayload.STREAM_CODEC,
                    ColdSweatReadoutNetworking::handleClient
            );
        }
    }
}
