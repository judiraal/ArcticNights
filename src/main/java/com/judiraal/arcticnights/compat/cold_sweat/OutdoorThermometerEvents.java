package com.judiraal.arcticnights.compat.cold_sweat;

import com.judiraal.arcticnights.compat.ConditionalEventBusSubscriber;
import com.judiraal.arcticnights.network.OutdoorThermometerNetworking;
import com.judiraal.arcticnights.util.ClimateService;
import com.judiraal.arcticnights.util.ClimateSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@ConditionalEventBusSubscriber(dependencies = {"cold_sweat"})
public final class OutdoorThermometerEvents {
    private static final int SYNC_INTERVAL_TICKS = 20;
    private static final Set<UUID> VALID_PLAYERS = new HashSet<>();

    private OutdoorThermometerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        if (player.tickCount % SYNC_INTERVAL_TICKS != 0) {
            return;
        }

        if (player.level().dimension() != Level.OVERWORLD || !ColdSweatCompat.showsAdvancedWorldTemperature(player)) {
            clearIfNeeded(player);
            return;
        }

        ClimateSnapshot snapshot = ClimateService.snapshot(player.level(), player.level().getBiome(player.blockPosition()), player.blockPosition());
        VALID_PLAYERS.add(player.getUUID());
        OutdoorThermometerNetworking.sync(player, snapshot.outdoorMinecraftTemperature());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        VALID_PLAYERS.remove(event.getEntity().getUUID());
    }

    private static void clearIfNeeded(ServerPlayer player) {
        if (VALID_PLAYERS.remove(player.getUUID())) {
            OutdoorThermometerNetworking.clear(player);
        }
    }
}
