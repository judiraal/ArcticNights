package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = ArcticNights.MOD_ID)
public final class ClimateVisualWeatherNetworking {
    private static final int SYNC_INTERVAL_TICKS = 5;

    private ClimateVisualWeatherNetworking() {
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

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    private static void sync(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        if (!(player.level() instanceof ServerLevel level) || !ArcticNights.appliesTo(level)) {
            PacketDistributor.sendToPlayer(player, new ClimateVisualWeatherPayload(
                    player.getId(),
                    false,
                    pos,
                    Biome.Precipitation.NONE
            ));
            return;
        }

        PacketDistributor.sendToPlayer(player, new ClimateVisualWeatherPayload(
                player.getId(),
                true,
                pos,
                ClimateService.precipitationAtAsVanillaType(level, level.getBiome(pos), pos)
        ));
    }

    private static void handleClient(ClimateVisualWeatherPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Entity entity = context.player().level().getEntity(payload.entityId());
            if (entity != null) {
                ClimateVisualWeatherState.apply(entity, payload);
            }
        });
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                ClimateVisualWeatherPayload.TYPE,
                ClimateVisualWeatherPayload.STREAM_CODEC,
                ClimateVisualWeatherNetworking::handleClient
        );
    }
}
