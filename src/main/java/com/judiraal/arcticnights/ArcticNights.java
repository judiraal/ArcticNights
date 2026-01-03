package com.judiraal.arcticnights;

import com.judiraal.arcticnights.util.ArcticSpawner;
import com.judiraal.arcticnights.worldgen.ArcticNightsDensityFunctionTypes;
import com.mojang.logging.LogUtils;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.data.internal.NeoForgeBiomeTagsProvider;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.living.SpawnClusterSizeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.slf4j.Logger;

@Mod(ArcticNights.MOD_ID)
@EventBusSubscriber
public class ArcticNights {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String MOD_ID = "arcticnights";
    public static final boolean SERENE_SEASONS = modLoaded("sereneseasons");
    public static final boolean IRIS = modLoaded("iris");

    private static boolean modLoaded(String modName) {
        return FMLLoader.getLoadingModList().getModFileById(modName) != null;
    }

    public ArcticNights(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, ArcticNightsConfig.SPEC);
        ArcticNightsDensityFunctionTypes.TYPES.register(modEventBus);
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (ArcticNightsConfig.firstTimeOfDay.get() > 0 && event.getLevel() instanceof ServerLevel serverLevel &&
                serverLevel.getGameTime() == 0 && serverLevel.dimension() == Level.OVERWORLD &&
                serverLevel.getLevelData() instanceof ServerLevelData data) {
            data.setDayTime(ArcticNightsConfig.firstTimeOfDay.get());
        }
    }

    @SubscribeEvent
    public static void onSpawnClusterSize(SpawnClusterSizeEvent event) {
        if (ArcticNightsConfig.arcticSpawning.isFalse()) return;
        if (event.getEntity().level() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            var factor = ArcticSpawner.spawnFactor(event.getEntity().getType(), event.getEntity().blockPosition(), level);
            if (factor < 1.0F) event.setSize((int)(event.getSize() * factor));
            else if (factor > 1.0F) {
                var correction = Mth.frac(factor) / (int)factor;
                event.setSize((int)(event.getSize() * correction));
            }
        }
    }

    @SubscribeEvent
    public static void onPotentialMobs(LevelEvent.PotentialSpawns event) {
        if (ArcticNightsConfig.arcticSpawning.isFalse()) return;
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD && event.getMobCategory() == MobCategory.MONSTER) {
            var totalWeight = 0;
            var removedWeight = 0;

            for (var data: event.getSpawnerDataList().toArray(MobSpawnSettings.SpawnerData[]::new)) {
                totalWeight += data.getWeight().asInt();
                var factor = ArcticSpawner.spawnFactor(data.type, event.getPos(), level);
                if (factor == 0.0F) {
                    event.removeSpawnerData(data);
                    removedWeight += data.getWeight().asInt();
                }
                else if (factor >= 2.0F) for (var i = 1; i < (int)factor; i++) {
                    event.addSpawnerData(data);
                    removedWeight -= data.getWeight().asInt();
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
    }
}
