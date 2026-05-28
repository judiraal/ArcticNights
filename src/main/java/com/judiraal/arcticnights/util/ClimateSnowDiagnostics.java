package com.judiraal.arcticnights.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class ClimateSnowDiagnostics {
    private ClimateSnowDiagnostics() {
    }

    public static ProfileResult profile(ServerLevel level, ChunkPos pos) {
        if (!(level.getChunk(pos.x, pos.z, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) {
            return new ProfileResult(pos, false, 0, 0, 0, 0, 0.0F, "none");
        }

        Diagnostics diagnostics = new Diagnostics();
        long dayTime = level.getDayTime();
        int columns = 0;
        int snowEligible = 0;
        int snowPresent = 0;
        int snowLayerTotal = 0;
        float snowFactorTotal = 0.0F;

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = pos.getMinBlockX() + localX;
                int z = pos.getMinBlockZ() + localZ;
                BlockPos surfacePos = new BlockPos(x, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, localX, localZ) + 1, z);
                if (surfacePos.getY() < level.getMinBuildHeight() || surfacePos.getY() >= level.getMaxBuildHeight()) {
                    diagnostics.record(SkipReason.OUT_OF_WORLD);
                    continue;
                }

                Holder<Biome> biome = level.getBiome(surfacePos);
                String sampleBiomeKey = biomeKey(biome);
                diagnostics.recordBiome(sampleBiomeKey);
                boolean exposedToSky = ClimateService.isExposedToSky(level, surfacePos);
                ClimateSnapshot.WeatherState weather = ClimateService.weatherStateAt(level, biome, exposedToSky);
                ClimateSnapshot snapshot = ClimateService.sampleSnapshot(level, biome, surfacePos, exposedToSky, weather, dayTime);
                BlockPos target = snowTarget(level, surfacePos);
                Holder<Biome> targetBiome = level.getBiome(target);
                SurfaceCategory surfaceCategory = surfaceCategory(level, target);
                SurfaceCategory supportCategory = surfaceCategory(level, target.below());
                diagnostics.recordTargetBiome(biomeKey(targetBiome));
                diagnostics.recordSurface(surfaceCategory);
                diagnostics.recordSupportSurface(supportCategory);
                BlockState targetState = level.getBlockState(target);
                diagnostics.recordSnowDepth(targetState);
                columns++;

                float snowFactor = ClimateService.snowAccumulationFactor(snapshot.outdoorMinecraftTemperature());
                snowFactorTotal += snowFactor;
                if (snowFactor > 0.0F) diagnostics.recordSnowExposure(snowFactor);
                if (targetState.is(Blocks.SNOW)) {
                    snowPresent++;
                    snowLayerTotal += targetState.getValue(SnowLayerBlock.LAYERS);
                }

                SkipReason failure = snowFailure(level, target, targetBiome, snapshot, sampleBiomeKey, diagnostics);
                if (failure == null) {
                    snowEligible++;
                } else {
                    diagnostics.record(failure);
                    diagnostics.recordSurfaceFailure(surfaceCategory);
                    diagnostics.recordSupportFailure(supportCategory);
                }
            }
        }

        float averageSnowFactor = columns == 0 ? 0.0F : snowFactorTotal / columns;
        return new ProfileResult(pos, true, columns, snowEligible, snowPresent, snowLayerTotal, averageSnowFactor, diagnostics.summary());
    }

    private static SkipReason snowFailure(ServerLevel level, BlockPos target, Holder<Biome> targetBiome, ClimateSnapshot snapshot, String sampleBiomeKey, Diagnostics diagnostics) {
        if (snapshot.precipitationKind() == ClimateSnapshot.PrecipitationKind.NONE) {
            diagnostics.recordNoBiomePrecipitationBiome(sampleBiomeKey);
            return SkipReason.NO_BIOME_PRECIPITATION;
        }
        if (ClimateService.snowAccumulationFactor(snapshot.outdoorMinecraftTemperature()) <= 0.0F) {
            return SkipReason.TEMPERATURE_TOO_WARM;
        }
        if (target.getY() < level.getMinBuildHeight() || target.getY() >= level.getMaxBuildHeight()) {
            return SkipReason.OUT_OF_WORLD;
        }
        if (level.getBrightness(LightLayer.BLOCK, target) >= 10) {
            return SkipReason.BLOCK_LIGHT;
        }

        BlockState state = level.getBlockState(target);
        if (state.is(Blocks.SNOW)) {
            if (state.getValue(SnowLayerBlock.LAYERS) >= 7) {
                return SkipReason.SNOW_LAYER_CAP;
            }
        } else if (!state.isAir() && !SeasonalGroundCover.canBeBuriedBySnow(state)) {
            return SkipReason.NO_SNOW_SURFACE;
        }

        if (!SnowAccumulation.canWeatherPlaceAt(level, target)) {
            return SkipReason.SNOW_CANNOT_SURVIVE;
        }
        if (!targetBiome.value().hasPrecipitation()) {
            return SkipReason.NO_BIOME_PRECIPITATION;
        }
        return null;
    }

    private static BlockPos snowTarget(ServerLevel level, BlockPos surfacePos) {
        if (level.getBlockState(surfacePos).is(Blocks.SNOW)) return surfacePos;
        BlockPos below = surfacePos.below();
        if (level.getBlockState(below).is(Blocks.SNOW)) return below;
        return surfacePos;
    }

    private static String biomeKey(Holder<Biome> biome) {
        return biome.unwrapKey()
                .map(key -> key.location().toString().replace(':', '/'))
                .orElse("unbound");
    }

    private static SurfaceCategory surfaceCategory(ServerLevel level, BlockPos target) {
        BlockState state = level.getBlockState(target);
        if (state.isAir()) return SurfaceCategory.AIR;
        if (state.is(Blocks.SNOW)) return SurfaceCategory.SNOW;
        if (SeasonalGroundCover.canBeBuriedBySnow(state)) return SurfaceCategory.BURYABLE;
        if (state.is(BlockTags.LEAVES)) return SurfaceCategory.LEAVES;
        if (state.is(Blocks.WATER)) return SurfaceCategory.WATER;
        if (state.is(Blocks.ICE)) return SurfaceCategory.ICE;
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) {
            return SurfaceCategory.SOIL;
        }
        if (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.GRANITE)
                || state.is(Blocks.DIORITE) || state.is(Blocks.ANDESITE) || state.is(Blocks.TUFF)) {
            return SurfaceCategory.STONE;
        }
        return state.blocksMotion() ? SurfaceCategory.SOLID : SurfaceCategory.OTHER;
    }

    private enum SkipReason {
        OUT_OF_WORLD("out_of_world"),
        NO_BIOME_PRECIPITATION("no_biome_precipitation"),
        TEMPERATURE_TOO_WARM("temperature_too_warm"),
        BLOCK_LIGHT("block_light"),
        NO_SNOW_SURFACE("no_snow_surface"),
        SNOW_CANNOT_SURVIVE("snow_cannot_survive"),
        SNOW_LAYER_CAP("snow_layer_cap");

        private final String key;

        SkipReason(String key) {
            this.key = key;
        }
    }

    private enum SurfaceCategory {
        AIR,
        SNOW,
        BURYABLE,
        LEAVES,
        WATER,
        ICE,
        SOIL,
        STONE,
        SOLID,
        OTHER
    }

    private static final class Diagnostics {
        private final EnumMap<SkipReason, Integer> skips = new EnumMap<>(SkipReason.class);
        private final EnumMap<SurfaceCategory, Integer> surfaces = new EnumMap<>(SurfaceCategory.class);
        private final EnumMap<SurfaceCategory, Integer> surfaceFailures = new EnumMap<>(SurfaceCategory.class);
        private final EnumMap<SurfaceCategory, Integer> supportSurfaces = new EnumMap<>(SurfaceCategory.class);
        private final EnumMap<SurfaceCategory, Integer> supportFailures = new EnumMap<>(SurfaceCategory.class);
        private final int[] snowDepths = new int[9];
        private final Map<String, Integer> biomes = new HashMap<>();
        private final Map<String, Integer> targetBiomes = new HashMap<>();
        private final Map<String, Integer> noBiomePrecipitationBiomes = new HashMap<>();
        private int snowFactorSamples;
        private float snowFactorTotal;
        private float snowFactorMin = Float.MAX_VALUE;
        private float snowFactorMax = Float.MIN_VALUE;

        private void record(SkipReason reason) {
            skips.merge(reason, 1, Integer::sum);
        }

        private void recordSnowExposure(float snowFactor) {
            snowFactorSamples++;
            snowFactorTotal += snowFactor;
            snowFactorMin = Math.min(snowFactorMin, snowFactor);
            snowFactorMax = Math.max(snowFactorMax, snowFactor);
        }

        private void recordSurface(SurfaceCategory category) {
            surfaces.merge(category, 1, Integer::sum);
        }

        private void recordSurfaceFailure(SurfaceCategory category) {
            surfaceFailures.merge(category, 1, Integer::sum);
        }

        private void recordSupportSurface(SurfaceCategory category) {
            supportSurfaces.merge(category, 1, Integer::sum);
        }

        private void recordSupportFailure(SurfaceCategory category) {
            supportFailures.merge(category, 1, Integer::sum);
        }

        private void recordSnowDepth(BlockState state) {
            if (state.is(Blocks.SNOW)) {
                snowDepths[Mth.clamp(state.getValue(SnowLayerBlock.LAYERS), 0, 8)]++;
            } else {
                snowDepths[0]++;
            }
        }

        private void recordBiome(String biome) {
            biomes.merge(biome, 1, Integer::sum);
        }

        private void recordTargetBiome(String biome) {
            targetBiomes.merge(biome, 1, Integer::sum);
        }

        private void recordNoBiomePrecipitationBiome(String biome) {
            noBiomePrecipitationBiomes.merge(biome, 1, Integer::sum);
        }

        private String summary() {
            StringBuilder builder = new StringBuilder();
            for (SkipReason reason : SkipReason.values()) {
                int count = skips.getOrDefault(reason, 0);
                if (count <= 0) continue;
                if (!builder.isEmpty()) builder.append(',');
                builder.append(reason.key).append('=').append(count);
            }
            appendSnowFactorSummary(builder);
            appendEnumSummary(builder, "surfaces", surfaces);
            appendEnumSummary(builder, "surface_failures", surfaceFailures);
            appendEnumSummary(builder, "support_surfaces", supportSurfaces);
            appendEnumSummary(builder, "support_failures", supportFailures);
            appendSnowDepthSummary(builder);
            appendMapSummary(builder, "biomes", biomes, 3);
            appendMapSummary(builder, "target_biomes", targetBiomes, 3);
            appendMapSummary(builder, "no_biome_precip_biomes", noBiomePrecipitationBiomes, 3);
            return builder.isEmpty() ? "none" : builder.toString();
        }

        private void appendSnowFactorSummary(StringBuilder builder) {
            if (snowFactorSamples <= 0) return;
            appendSectionPrefix(builder, "snow_factor");
            builder.append("samples:").append(snowFactorSamples)
                    .append("|avg:").append(decimal(snowFactorTotal / snowFactorSamples))
                    .append("|min:").append(decimal(snowFactorMin))
                    .append("|max:").append(decimal(snowFactorMax));
        }

        private <E extends Enum<E>> void appendEnumSummary(StringBuilder builder, String label, EnumMap<E, Integer> values) {
            if (values.isEmpty()) return;
            appendSectionPrefix(builder, label);
            boolean first = true;
            for (Map.Entry<E, Integer> entry : values.entrySet()) {
                if (entry.getValue() <= 0) continue;
                if (!first) builder.append('|');
                builder.append(entry.getKey().name().toLowerCase(Locale.ROOT)).append(':').append(entry.getValue());
                first = false;
            }
        }

        private void appendSnowDepthSummary(StringBuilder builder) {
            appendSectionPrefix(builder, "snow_depths");
            boolean first = true;
            for (int i = 0; i < snowDepths.length; i++) {
                int count = snowDepths[i];
                if (count <= 0) continue;
                if (!first) builder.append('|');
                builder.append(i).append(':').append(count);
                first = false;
            }
        }

        private void appendMapSummary(StringBuilder builder, String label, Map<String, Integer> values, int limit) {
            if (values.isEmpty()) return;
            appendSectionPrefix(builder, label);
            values.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(limit)
                    .forEach(entry -> {
                        if (builder.charAt(builder.length() - 1) != '=') builder.append('|');
                        builder.append(entry.getKey()).append(':').append(entry.getValue());
                    });
        }

        private void appendSectionPrefix(StringBuilder builder, String label) {
            if (!builder.isEmpty()) builder.append(',');
            builder.append(label).append('=');
        }
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    public record ProfileResult(ChunkPos pos, boolean loaded, int columns, int snowEligible, int snowPresent, int snowLayerTotal, float averageSnowFactor, String diagnostics) {
    }
}
