package com.judiraal.arcticnights;

import com.judiraal.arcticnights.util.ReverseTimeOfDay;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Quaternionf;
import org.slf4j.Logger;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.INVERTED;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.POWER;

@Mod(ArcticNights.MOD_ID)
@EventBusSubscriber
public class ArcticNights {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String MOD_ID = "arcticnights";
    private static final boolean SERENE_SEASONS = modLoaded("sereneseasons");
    private static final boolean IRIS = modLoaded("iris");

    private static boolean modLoaded(String modName) {
        return FMLLoader.getLoadingModList().getModFileById(modName) != null;
    }

    public ArcticNights(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, ArcticNightsConfig.SPEC);
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (ArcticNightsConfig.firstTimeOfDay.get() > 0 && event.getLevel() instanceof ServerLevel serverLevel &&
                serverLevel.getGameTime() == 0 && serverLevel.dimension() == Level.OVERWORLD &&
                serverLevel.getLevelData() instanceof ServerLevelData data) {
            data.setDayTime(ArcticNightsConfig.firstTimeOfDay.get());
        }
    }

    public static int seasonalSkyDarken(LevelReader level, BlockPos pos) {
        if (level instanceof ServerLevel) {
            return ((SkyDarkenHolder)level.getChunk(pos)).msc$getSkyDarken();
        }
        return level.getSkyDarken();
    }

    public static float seasonalTimeOfDay(Level level, ChunkPos chunkPos, float original) {
        float distanceFactor = Mth.abs((distanceFactor(chunkPos) - 0.5F) * 2);
        float seasonalFactor = seasonalFactor(level);
        if (seasonalFactor < 0)
            return Mth.lerp(-seasonalFactor * distanceFactor, original, (float) Math.pow(original - 0.5F, 3) * 4 + 0.5F);
        else {
            var modified = Math.pow(Mth.frac(original + 0.5F) - 0.5F, 3) * 4 + 1.0F;
            return (float) Mth.lerp((double) seasonalFactor * distanceFactor, original, Mth.frac(modified));
        }
    }

    public static int calcSeasonalSkyDarken(Level level, ChunkPos pos) {
        double d0 = 1.0 - (double)(level.getRainLevel(1.0F) * 5.0F) / 16.0;
        double d1 = 1.0 - (double)(level.getThunderLevel(1.0F) * 5.0F) / 16.0;
        double d2 = 0.5 + 2.0 * Mth.clamp(Mth.cos(seasonalTimeOfDay(level, pos, level.getTimeOfDay(1.0F)) * (Mth.PI * 2)), -0.25, 0.25);
        return (int)((1.0 - d2 * d0 * d1) * 11.0);
    }

    public static void updateSignalStrength(BlockState blockState, Level level, BlockPos pos) {
        int i = level.getBrightness(LightLayer.SKY, pos) - seasonalSkyDarken(level, pos);
        boolean flag = blockState.getValue(INVERTED);
        if (flag) {
            i = 15 - i;
        } else {
            float timeOfDay = seasonalTimeOfDay(level, new ChunkPos(pos), level.getTimeOfDay(1.0F));
            if (timeOfDay > 0.25F && timeOfDay < 0.75F) i = 0;
        }
        i = Mth.clamp(i, 0, 15);
        if (blockState.getValue(POWER) != i) level.setBlock(pos, blockState.setValue(POWER, i), 3);
    }

    /**
     * @param chunkPos
     * @return float between 0 (north-pole), 0.5 (equator) and 1 (south-pole), such that z=0 is 0.25
     */
    private static float distanceFactor(ChunkPos chunkPos) {
        int c = ArcticNightsConfig.circumferenceBlockDistance.get() >> 4;
        float distance = (float) Math.floorMod(chunkPos.z + (c >> 3), c) / c * 2;
        if (distance > 1) distance = 2 - distance;
        return distance;
    }

    /**
     * @param level
     * @return float between 1 (summer) and -1 (winter)
      */
    public static float seasonalFactor(Level level) {
        int day = 80;
        int dpy = ArcticNightsConfig.daysPerSeason.get() << 2;
        if (dpy > 0) {
            if (SERENE_SEASONS) {
                day = SereneSeasonsCompat.HOLDER.getDay(level);
            } else {
                day = (int)((level.getDayTime() / 24000) * 96 / dpy);
            }
        }
        return Mth.cos((float)(day - 32) / 48 * Mth.PI);
    }

    /**
     * @param distanceFactor
     * @param seasonalFactor
     * @return seasonally corrected float between 0 (north-pole), 0.5 (equator) and 1 (south-pole), such that z=0 is 0.25
     */
    private static float angleFactor(float distanceFactor, float seasonalFactor) {
        return Mth.lerp(-seasonalFactor / 2 + 0.5F, 0.5F, distanceFactor);
    }

    public static float seasonalDistanceEffect(Level level, ChunkPos chunkPos) {
        float distance = distanceFactor(chunkPos);
        return Mth.lerp(0.5F - (seasonalFactor(level) / 2), 0.5F, distance);
    }

    public static double seasonalClimateTemperature(int zPos) {
        int c = ArcticNightsConfig.circumferenceBlockDistance.get()>>4;
        return Mth.cos((float) (zPos - (c<<1)) / (c<<2) * Mth.PI) * 0.55F + 0.05F;
    }

    private static int seasonalDayTime(Level level, ChunkPos chunkPos, int dayTimeTicks) {
        float seasonalTimeOfDay = seasonalTimeOfDay(level, chunkPos, ReverseTimeOfDay.getTimeOfDay(dayTimeTicks));
        return ReverseTimeOfDay.reverseTimeOfDay(seasonalTimeOfDay);
    }

    private static class SereneSeasonsCompat {
        private static final SereneSeasonsCompat HOLDER = new SereneSeasonsCompat();

        int getDay(Level level) {
            return sereneseasons.api.season.SeasonHelper.getSeasonState(level).getDay();
        }
    }

    public static class Client {
        private static long lastAngleTime;
        private static float lastAngle;
        private static int lastDayTimeTicks = -1;
        private static int lastSeasonalDayTime;

        public static float currentSunAngle() {
            long angleTime = System.nanoTime();
            if (angleTime - lastAngleTime > 10000000L) {
                lastAngleTime = angleTime;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.level != null) {
                    float angleFactor = angleFactor(distanceFactor(mc.player.chunkPosition()), seasonalFactor(mc.level));
                    lastAngle = (angleFactor * 0.9F - 0.45F) * Mth.PI;
                } else
                    lastAngle = 0;
            }
            return lastAngle;
        }

        public static Quaternionf currentSunRotation() {
            return Axis.YP.rotationDegrees(-90f).rotateZ(IRIS && !IrisHelper.isFallback() ? 0 : currentSunAngle());
        }

        public static float seasonalTimeOfDay(float original) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null)
                return ArcticNights.seasonalTimeOfDay(mc.level, mc.player.chunkPosition(), original);
            return original;
        }

        public static int seasonalDayTime(int dayTimeTicks) {
            if (lastDayTimeTicks == dayTimeTicks) return lastSeasonalDayTime;
            lastDayTimeTicks = dayTimeTicks;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null)
                return lastSeasonalDayTime = ArcticNights.seasonalDayTime(mc.level, mc.player.chunkPosition(), dayTimeTicks);
            return lastSeasonalDayTime = dayTimeTicks;
        }
    }

    public static class IrisHelper {
        static boolean isFallback() {
            return Iris.getCurrentPack().isEmpty();
        }
    }
}
