package com.judiraal.arcticnights;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;
import org.joml.Quaternionf;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.INVERTED;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.POWER;

@Mod(ArcticNights.MOD_ID)
public class ArcticNights {
    public static final String MOD_ID = "arcticnights";
    private static final boolean SERENE_SEASONS = modLoaded("sereneseasons");

    private static boolean modLoaded(String modName) {
        return FMLLoader.getLoadingModList().getModFileById(modName) != null;
    }

    public ArcticNights(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, ArcticNightsConfig.SPEC);
    }

    public static int seasonalSkyDarken(LevelReader level, BlockPos pos) {
        if (level instanceof ServerLevel) {
            return ((SkyDarkenHolder)level.getChunk(pos)).msc$getSkyDarken();
        }
        return level.getSkyDarken();
    }

    public static float seasonalTimeOfDay(Level level, ChunkPos chunkPos, float original) {
        float distance = Math.abs(seasonalDistanceEffect(level, chunkPos) - 0.5F) * 2;
        return Mth.lerp(distance, original, (float) Math.pow(original - 0.5F, 3) * 4 + 0.5F);
    }

    public static int calcSeasonalSkyDarken(Level level, ChunkPos pos) {
        double d0 = 1.0 - (double)(level.getRainLevel(1.0F) * 5.0F) / 16.0;
        double d1 = 1.0 - (double)(level.getThunderLevel(1.0F) * 5.0F) / 16.0;
        double d2 = 0.5 + 2.0 * Mth.clamp(Mth.cos(seasonalTimeOfDay(level, pos, level.getTimeOfDay(1.0F)) * (float) (Math.PI * 2)), -0.25, 0.25);
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

    public static float seasonalDistanceEffect(Level level, ChunkPos chunkPos) {
        int c = ArcticNightsConfig.circumferenceChunkDistance;
        float distance = (float) Math.floorMod(chunkPos.z + (c >> 3), c) / c * 2;
        if (distance > 1) distance = 2 - distance;
        int day = SERENE_SEASONS ? SereneSeasonsCompat.HOLDER.getDay(level) : 80;
        return Mth.lerp(1 - Math.abs(Mth.cos((float)(day - 32) / 96 * (float) Math.PI)), 0.5F, distance);
    }

    public static double seasonalClimateTemperature(int zPos) {
        return Mth.cos((float) (zPos - 5000) / 10000 * (float) Math.PI) * 1.2F;
    }

    private static class SereneSeasonsCompat {
        private static final SereneSeasonsCompat HOLDER = new SereneSeasonsCompat();

        int getDay(Level level) {
            return sereneseasons.api.season.SeasonHelper.getSeasonState(level).getDay();
        }
    }

    public static class Client {
        public static Quaternionf currentAngle() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
                float distance = seasonalDistanceEffect(mc.level, mc.player.chunkPosition());
                return Axis.YP.rotationDegrees(-90f).rotateZ((distance * 0.8f - 0.4f) * (float) Math.PI);
            }
            return Axis.YP.rotationDegrees(-90f);
        }

        public static float seasonalTimeOfDay(float original) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null)
                return ArcticNights.seasonalTimeOfDay(mc.level, mc.player.chunkPosition(), original);
            return original;
        }
    }
}
