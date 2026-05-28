package com.judiraal.arcticnights;

import com.judiraal.arcticnights.command.ClimateCatchupCommands;
import com.judiraal.arcticnights.compat.ConditionalSubscribers;
import com.judiraal.arcticnights.util.ArcticSpawner;
import com.judiraal.arcticnights.util.ClimateCatchupQueue;
import com.judiraal.arcticnights.util.ClimateDebugWeatherNetworking;
import com.judiraal.arcticnights.util.ClimatePlayerHarness;
import com.judiraal.arcticnights.util.ClimateService;
import com.judiraal.arcticnights.util.ClimateTickrateCatchup;
import com.judiraal.arcticnights.util.ClimateVisualWeatherNetworking;
import com.judiraal.arcticnights.util.UndeadSpawnDiagnostics;
import com.judiraal.arcticnights.worldgen.ArcticNightsDensityFunctionTypes;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.data.internal.NeoForgeBiomeTagsProvider;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.living.SpawnClusterSizeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.Locale;

@Mod(ArcticNights.MOD_ID)
@EventBusSubscriber
public class ArcticNights {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String MOD_ID = "arcticnights";
    public static final boolean SERENE_SEASONS = modLoaded("sereneseasons");
    public static final boolean IRIS = modLoaded("iris");
    public static final boolean COLD_SWEAT = modLoaded("cold_sweat");

    private static boolean modLoaded(String modName) {
        return FMLLoader.getLoadingModList().getModFileById(modName) != null;
    }

    public static boolean appliesTo(Level level) {
        return level.dimension() == Level.OVERWORLD;
    }

    public ArcticNights(IEventBus modEventBus, ModContainer modContainer) {
        ConditionalSubscribers.inject(modContainer);
        modContainer.registerConfig(ModConfig.Type.COMMON, ArcticNightsConfig.SPEC);
        ArcticNightsAttachments.register(modEventBus);
        modEventBus.addListener(ClimateDebugWeatherNetworking::registerPayloads);
        modEventBus.addListener(ClimateVisualWeatherNetworking::registerPayloads);
        ArcticNightsDensityFunctionTypes.TYPES.register(modEventBus);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ClimatePlayerHarness.onServerStopped(event);
        ClimateService.clearDebugWeatherOffset(event.getServer());
        ClimateCatchupQueue.onServerStopped(event);
        ClimateTickrateCatchup.onServerStopped(event);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ClimateCatchupCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ClimatePlayerHarness.onServerTick(event);
        ClimateCatchupQueue.onServerTick(event);
        ClimateTickrateCatchup.onServerTick(event);
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (ArcticNightsConfig.firstTimeOfDay.get() > 0 && event.getLevel() instanceof ServerLevel serverLevel &&
                serverLevel.getGameTime() == 0 && appliesTo(serverLevel) &&
                serverLevel.getLevelData() instanceof ServerLevelData data) {
            data.setDayTime(ArcticNightsConfig.firstTimeOfDay.get());
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        ClimateCatchupQueue.onChunkLoad(event);
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        ClimateCatchupQueue.onChunkUnload(event);
    }

    @SubscribeEvent
    public static void onSpawnClusterSize(SpawnClusterSizeEvent event) {
        if (!ArcticNightsFeatures.arcticSpawning()) return;
        if (event.getEntity().level() instanceof ServerLevel level && appliesTo(level)) {
            var factor = ArcticSpawner.spawnFactor(event.getEntity().getType(), event.getEntity().blockPosition(), level);
            if (factor > 0.0F && factor < 1.0F) event.setSize(Math.max(1, (int)(event.getSize() * factor)));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSpawnPlacementCheckDiagnostics(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!spawnDiagnosticsLogging()
                || !ArcticNightsFeatures.arcticSpawning()
                || event.getSpawnType() != MobSpawnType.NATURAL) return;
        ServerLevel level = event.getLevel().getLevel();
        if (!appliesTo(level)) return;

        String result = switch (event.getResult()) {
            case SUCCEED -> "placement_forced_pass";
            case FAIL -> "placement_fail_event";
            case DEFAULT -> event.getDefaultResult() ? "placement_pass" : "placement_fail_default";
        };
        UndeadSpawnDiagnostics.recordLifecycle(level, event.getPos(), event.getEntityType(), result);
    }

    @SubscribeEvent
    public static void onSpawnPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (!ArcticNightsFeatures.arcticSpawning() || event.getSpawnType() != MobSpawnType.NATURAL) return;
        ServerLevel level = event.getLevel().getLevel();
        if (!appliesTo(level)) return;
        BlockPos pos = event.getEntity().blockPosition();
        if (event.getEntity().getType() == EntityType.CREEPER) {
            var probe = ArcticSpawner.creeperSpawnProbe(pos, level);
            if (probe.factor() == 0.0F) {
                event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
                logCreeperSpawn("deny", event.getSpawnType(), pos, probe);
            }
        } else if (ArcticSpawner.spawnFactor(event.getEntity().getType(), pos, level) == 0.0F) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSpawnPositionCheckDiagnostics(MobSpawnEvent.PositionCheck event) {
        if (!spawnDiagnosticsLogging()
                || !ArcticNightsFeatures.arcticSpawning()
                || event.getSpawnType() != MobSpawnType.NATURAL) return;
        ServerLevel level = event.getLevel().getLevel();
        if (!appliesTo(level)) return;

        String result = switch (event.getResult()) {
            case SUCCEED -> "position_forced_pass";
            case FAIL -> "position_fail_event";
            case DEFAULT -> {
                boolean rules = event.getEntity().checkSpawnRules(level, event.getSpawnType());
                boolean obstruction = rules && event.getEntity().checkSpawnObstruction(level);
                if (!rules) {
                    yield "position_fail_rules";
                }
                yield obstruction ? "position_pass" : "position_fail_obstruction";
            }
        };
        UndeadSpawnDiagnostics.recordLifecycle(level, event.getEntity().blockPosition(), event.getEntity().getType(), result);
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!spawnDiagnosticsLogging() || event.getEntity().getType() != EntityType.CREEPER) return;
        ServerLevel level = event.getLevel().getLevel();
        if (!appliesTo(level)) return;
        BlockPos pos = event.getEntity().blockPosition();
        logCreeperSpawn("finalize", event.getSpawnType(), pos, ArcticSpawner.creeperSpawnProbe(pos, level));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFinalizeSpawnDiagnostics(FinalizeSpawnEvent event) {
        if (!spawnDiagnosticsLogging()
                || !ArcticNightsFeatures.arcticSpawning()
                || event.getSpawnType() != MobSpawnType.NATURAL) return;
        ServerLevel level = event.getLevel().getLevel();
        if (!appliesTo(level)) return;

        boolean canceled = event.isCanceled() || event.isSpawnCancelled();
        UndeadSpawnDiagnostics.recordFinalize(
                level,
                event.getEntity(),
                canceled ? "finalize_canceled" : "finalize",
                !canceled);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity().getType() != EntityType.CREEPER) return;
        if (!(event.getLevel() instanceof ServerLevel level) || !appliesTo(level)) return;

        boolean arcticSpawning = ArcticNightsFeatures.arcticSpawning();
        boolean spawnDiagnostics = spawnDiagnosticsLogging();
        if (!arcticSpawning && !spawnDiagnostics) return;

        BlockPos pos = event.getEntity().blockPosition();
        ArcticSpawner.CreeperSpawnProbe probe = ArcticSpawner.creeperSpawnProbe(pos, level);
        boolean denied = arcticSpawning && probe.factor() == 0.0F;
        if (denied) {
            event.setCanceled(true);
            event.getEntity().discard();
        }

        if (!spawnDiagnostics) return;
        String action = denied ? "join-deny" : "join";
        logCreeperJoin(action, event, pos, probe, denied);
        if (denied) {
            logCreeperJoinStack(action, event, pos);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoinLevelDiagnostics(EntityJoinLevelEvent event) {
        if (!spawnDiagnosticsLogging()) return;
        if (!(event.getLevel() instanceof ServerLevel level) || !appliesTo(level)) return;
        UndeadSpawnDiagnostics.recordJoin(level, event.getEntity(), event.isCanceled());
    }

    private static void logCreeperSpawn(String action, MobSpawnType spawnType, BlockPos pos, ArcticSpawner.CreeperSpawnProbe probe) {
        if (!spawnDiagnosticsLogging()) return;
        LOGGER.debug("ANSPAWN creeper {} spawnType={} gate={} pos={} {} {} biome={} tempC={} mcTemp={} factor={} cave={} lava={}",
                action,
                spawnType,
                ArcticNightsFeatures.arcticSpawning() ? "active" : "disabled",
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                probe.biomeId(),
                c(probe.estimatedCelsius()),
                f(probe.minecraftTemperature()),
                f(probe.factor()),
                f(probe.caveFactor()),
                probe.nearUndergroundLavaLake());
    }

    private static void logCreeperJoin(String action, EntityJoinLevelEvent event, BlockPos pos, ArcticSpawner.CreeperSpawnProbe probe, boolean denied) {
        if (!spawnDiagnosticsLogging()) return;
        LOGGER.debug("ANSPAWN creeper {} loadedFromDisk={} canceled={} gate={} uuid={} pos={} {} {} biome={} tempC={} mcTemp={} factor={} cave={} lava={} tags={} source={}",
                action,
                event.loadedFromDisk(),
                denied,
                ArcticNightsFeatures.arcticSpawning() ? "active" : "disabled",
                event.getEntity().getUUID(),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                probe.biomeId(),
                c(probe.estimatedCelsius()),
                f(probe.minecraftTemperature()),
                f(probe.factor()),
                f(probe.caveFactor()),
                probe.nearUndergroundLavaLake(),
                event.getEntity().getTags(),
                stackSourceHint());
    }

    private static void logCreeperJoinStack(String action, EntityJoinLevelEvent event, BlockPos pos) {
        if (!spawnDiagnosticsLogging()) return;
        LOGGER.debug("ANSPAWN creeper stack action={} loadedFromDisk={} uuid={} pos={} {} {}",
                action,
                event.loadedFromDisk(),
                event.getEntity().getUUID(),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                new Throwable("Creeper EntityJoinLevelEvent source stack"));
    }

    private static boolean spawnDiagnosticsLogging() {
        return ArcticNightsFeatures.spawnDiagnostics() && LOGGER.isDebugEnabled();
    }

    private static String stackSourceHint() {
        StringBuilder hint = new StringBuilder();
        int count = 0;
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String className = frame.getClassName();
            if (isNoiseStackFrame(className)) continue;
            if (count++ > 0) hint.append(" <- ");
            hint.append(simpleClassName(className)).append('.').append(frame.getMethodName()).append(':').append(frame.getLineNumber());
            if (count >= 8) break;
        }
        return hint.isEmpty() ? "unknown" : hint.toString();
    }

    private static boolean isNoiseStackFrame(String className) {
        return className.equals(Thread.class.getName())
                || className.equals(ArcticNights.class.getName())
                || className.startsWith("java.")
                || className.startsWith("jdk.")
                || className.startsWith("sun.")
                || className.startsWith("org.slf4j.")
                || className.startsWith("com.mojang.logging.")
                || className.startsWith("net.neoforged.bus.")
                || className.startsWith("net.neoforged.fml.")
                || className.startsWith("cpw.mods.");
    }

    private static String simpleClassName(String className) {
        int index = className.lastIndexOf('.');
        return index < 0 ? className : className.substring(index + 1);
    }

    private static String c(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String f(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    @SubscribeEvent
    public static void onPotentialMobs(LevelEvent.PotentialSpawns event) {
        if (!ArcticNightsFeatures.arcticSpawning()) return;
        if (event.getLevel() instanceof ServerLevel level && appliesTo(level) && event.getMobCategory() == MobCategory.MONSTER) {
            var totalWeight = 0;
            var removedWeight = 0;

            for (var data: event.getSpawnerDataList().toArray(MobSpawnSettings.SpawnerData[]::new)) {
                totalWeight += data.getWeight().asInt();
                var factor = ArcticSpawner.spawnFactor(data.type, event.getPos(), level);
                if (factor == 0.0F) {
                    event.removeSpawnerData(data);
                    removedWeight += data.getWeight().asInt();
                    UndeadSpawnDiagnostics.recordPotential(level, event.getPos(), data.type, factor, "removed_zero");
                }
                else if (factor < 1.0F && event.getLevel().getRandom().nextFloat() > factor) {
                    event.removeSpawnerData(data);
                    removedWeight += data.getWeight().asInt();
                    UndeadSpawnDiagnostics.recordPotential(level, event.getPos(), data.type, factor, "removed_roll");
                }
                else if (factor >= 2.0F) {
                    int copies = (int)factor;
                    for (var i = 1; i < copies; i++) {
                        event.addSpawnerData(data);
                        removedWeight -= data.getWeight().asInt();
                    }
                    UndeadSpawnDiagnostics.recordPotential(level, event.getPos(), data.type, factor, "boosted_" + copies + "x");
                } else {
                    UndeadSpawnDiagnostics.recordPotential(level, event.getPos(), data.type, factor, factor < 1.0F ? "kept_roll" : "kept");
                }
            }
            if (removedWeight > (totalWeight>>1) && event.getLevel().getRandom().nextFloat() > Mth.square((float)(totalWeight-removedWeight)/totalWeight)) {
                for (var data: event.getSpawnerDataList().toArray()) event.removeSpawnerData((MobSpawnSettings.SpawnerData) data);
            }
        }
    }

    public static final class Blocks {
        public static final TagKey<Block> TORNADO_PROTECTED =
                TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "tornado_protected"));
        public static final TagKey<Block> SNOW_BURYABLE_VEGETATION =
                TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "snow_buryable_vegetation"));
        public static final TagKey<Block> SEASONAL_REGROWTH_SUBSTRATE =
                TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "seasonal_regrowth_substrate"));
        public static final TagKey<Block> SEASONAL_SHORT_GRASS =
                TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "seasonal_short_grass"));
        public static final TagKey<Block> SEASONAL_FOREST_LITTER =
                TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "seasonal_forest_litter"));
    }
}
