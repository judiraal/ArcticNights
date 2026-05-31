package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.judiraal.arcticnights.mixin.minecraft.BiomeManagerAccessor;
import com.judiraal.arcticnights.mixin.minecraft.ChunkMapAccessor;
import com.judiraal.arcticnights.mixin.minecraft.LevelRandomAccessor;
import com.judiraal.arcticnights.mixin.minecraft.ServerChunkCacheAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.LinearCongruentialGenerator;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.Tags;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.config.SeasonsConfig;
import sereneseasons.init.ModTags;
import sereneseasons.season.SeasonHooks;

public final class SnowMeltHandler {
    private static final float CLIMATE_MELT_CHANCE = 1.0F / 16.0F;
    private static final double CLIMATE_MELT_PLAYER_DISTANCE = 128.0D;
    private static final double CLIMATE_MELT_PLAYER_DISTANCE_SQR =
            CLIMATE_MELT_PLAYER_DISTANCE * CLIMATE_MELT_PLAYER_DISTANCE;

    private SnowMeltHandler() {}

    public static void meltInChunk(LevelChunk chunkIn, float meltChance, SeasonsConfig.SeasonProperties seasonProperties) {
        meltInChunk(chunkIn, meltChance, seasonProperties.subSeason());
    }

    public static void meltClimateInLoadedChunks(ServerLevel level) {
        meltClimateInLoadedChunks(level, getSubSeason(level));
    }

    public static void meltClimateInLoadedChunks(ServerLevel level, Season.SubSeason subSeason) {
        int rolls = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        if (rolls <= 0) return;

        ChunkMap chunkMap = ((ServerChunkCacheAccessor) level.getChunkSource()).arcticnights$chunkMap();
        ChunkMapAccessor chunkMapAccessor = (ChunkMapAccessor) chunkMap;
        for (ChunkHolder holder : chunkMapAccessor.arcticnights$visibleChunkMap().values()) {
            LevelChunk chunk = holder.getTickingChunk();
            if (chunk == null) continue;

            ChunkPos chunkPos = chunk.getPos();
            if (!isPlayerCloseEnoughForClimateMelt(level, chunkPos)
                    || !level.shouldTickBlocksAt(chunkPos.toLong())) continue;

            for (int roll = 0; roll < rolls; roll++) {
                meltInChunk(chunk, CLIMATE_MELT_CHANCE, subSeason);
            }
        }
    }

    private static boolean isPlayerCloseEnoughForClimateMelt(ServerLevel level, ChunkPos chunkPos) {
        double chunkX = chunkPos.getMiddleBlockX();
        double chunkZ = chunkPos.getMiddleBlockZ();

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;

            double dx = player.getX() - chunkX;
            double dz = player.getZ() - chunkZ;
            if (dx * dx + dz * dz <= CLIMATE_MELT_PLAYER_DISTANCE_SQR) {
                return true;
            }
        }

        return false;
    }

    private static void meltInChunk(LevelChunk chunkIn, float meltChance, Season.SubSeason subSeason) {
        final ServerLevel level = (ServerLevel) chunkIn.getLevel();

        if (meltChance <= 0.0F || level.random.nextFloat() >= meltChance) return;

        BlockPos.MutableBlockPos surfacePos = randomSurfacePos(level, chunkIn);

        BlockState surfaceState = level.getBlockState(surfacePos);
        if (!surfaceState.is(Blocks.SNOW)) {
            surfacePos.move(Direction.DOWN);
            surfaceState = level.getBlockState(surfacePos);
        }

        if (!shouldMeltAt(level, chunkIn, surfacePos, subSeason)) return;
        doMelt(surfaceState, level, surfacePos);
        tryMeltDown(level, surfacePos, 1);
    }

    private static BlockPos.MutableBlockPos randomSurfacePos(ServerLevel level, LevelChunk chunk) {
        int randomBits = nextBlockRandomBits(level);
        int localX = randomBits & 15;
        int localZ = randomBits >> 8 & 15;
        ChunkPos chunkPos = chunk.getPos();
        int x = chunkPos.getMinBlockX() + localX;
        int z = chunkPos.getMinBlockZ() + localZ;
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, localX, localZ) + 1;
        return new BlockPos.MutableBlockPos(x, y, z);
    }

    private static int nextBlockRandomBits(ServerLevel level) {
        LevelRandomAccessor accessor = (LevelRandomAccessor) level;
        int randValue = accessor.arcticnights$randValue() * 3 + 1013904223;
        accessor.arcticnights$randValue(randValue);
        return randValue >> 2;
    }

    private static final java.util.WeakHashMap<ServerLevel, SeasonCache> SEASON_CACHE = new java.util.WeakHashMap<>();

    public static Season.SubSeason getSubSeason(ServerLevel level) {
        final long bucket = level.getGameTime() / 20L;
        SeasonCache c = SEASON_CACHE.get(level);
        if (c == null || c.bucket != bucket) {
            final var state = SeasonHelper.getSeasonState(level);
            final Season.SubSeason ss = state.getSubSeason(); // typical SS API
            c = new SeasonCache(bucket, ss);
            SEASON_CACHE.put(level, c);
        }
        return c.subSeason;
    }

    private static final class SeasonCache {
        final long bucket;
        final Season.SubSeason subSeason;
        SeasonCache(long bucket, Season.SubSeason subSeason) {
            this.bucket = bucket;
            this.subSeason = subSeason;
        }
    }

    public static boolean shouldMeltAt(ServerLevel level, BlockPos pos) {
        return shouldMeltAt(level, pos, getSubSeason(level));
    }

    public static boolean shouldMeltAt(ServerLevel level, BlockPos pos, Season.SubSeason subSeason) {
        final Holder<Biome> biomeHolder = level.getBiome(pos);
        return shouldMeltAt(level, null, biomeHolder, pos, subSeason);
    }

    private static boolean shouldMeltAt(ServerLevel level, LevelChunk chunk, BlockPos pos, Season.SubSeason subSeason) {
        final Holder<Biome> biomeHolder = biomeInLoadedChunks(level, chunk, pos);
        return shouldMeltAt(level, chunk, biomeHolder, pos, subSeason);
    }

    private static boolean shouldMeltAt(ServerLevel level, LevelChunk chunkOrNull, Holder<Biome> biomeHolder, BlockPos pos, Season.SubSeason subSeason) {
        if (biomeHolder.is(Tags.Biomes.IS_CAVE)) return false;

        if (ArcticNightsFeatures.fullMeltingUsesClimate()) {
            ClimateSnapshot.SnowBehavior snowBehavior = ClimateTickrateCatchup.isReplayingHistoricalMelt(level)
                    ? ClimateTickrateCatchup.historicalSnowBehavior(level, biomeHolder, pos, true)
                    : ClimateService.snowBehaviorAt(level, biomeHolder, pos);
            if (snowBehavior == ClimateSnapshot.SnowBehavior.PERSISTENT) return false;
            if (snowBehavior == ClimateSnapshot.SnowBehavior.TRANSITIONAL_SURFACE) {
                final int surfaceY = heightAt(level, chunkOrNull, Heightmap.Types.WORLD_SURFACE, pos);
                return pos.getY() > surfaceY;
            }

            return true;
        }

        if (biomeHolder.is(ModTags.Biomes.BLACKLISTED_BIOMES) && !biomeHolder.is(Biomes.RIVER)) return false;

        final float temp = SeasonHooks.getBiomeTemperatureInSeason(subSeason, biomeHolder, pos);
        if (temp < -0.15F) return false;
        if (temp < 0.15F) {
            final int surfaceY = heightAt(level, chunkOrNull, Heightmap.Types.WORLD_SURFACE, pos);
            return pos.getY() > surfaceY;
        }

        return true;
    }

    private static int heightAt(ServerLevel level, LevelChunk chunkOrNull, Heightmap.Types heightmapType, BlockPos pos) {
        if (chunkOrNull == null) return level.getHeight(heightmapType, pos.getX(), pos.getZ());
        return chunkOrNull.getHeight(heightmapType, pos.getX() & 15, pos.getZ() & 15) + 1;
    }

    // Mirrors BiomeManager#getBiome, but avoids ServerChunkCache#getChunk futures in the hot snow-melt path.
    private static Holder<Biome> biomeInLoadedChunks(ServerLevel level, LevelChunk originChunk, BlockPos pos) {
        int x = pos.getX() - 2;
        int y = pos.getY() - 2;
        int z = pos.getZ() - 2;
        int quartX = x >> 2;
        int quartY = y >> 2;
        int quartZ = z >> 2;
        double localX = (double)(x & 3) / 4.0D;
        double localY = (double)(y & 3) / 4.0D;
        double localZ = (double)(z & 3) / 4.0D;
        int closestCorner = 0;
        double closestDistance = Double.POSITIVE_INFINITY;
        long biomeZoomSeed = ((BiomeManagerAccessor)level.getBiomeManager()).arcticnights$biomeZoomSeed();

        for (int corner = 0; corner < 8; corner++) {
            boolean lowX = (corner & 4) == 0;
            boolean lowY = (corner & 2) == 0;
            boolean lowZ = (corner & 1) == 0;
            int sampleQuartX = lowX ? quartX : quartX + 1;
            int sampleQuartY = lowY ? quartY : quartY + 1;
            int sampleQuartZ = lowZ ? quartZ : quartZ + 1;
            double sampleLocalX = lowX ? localX : localX - 1.0D;
            double sampleLocalY = lowY ? localY : localY - 1.0D;
            double sampleLocalZ = lowZ ? localZ : localZ - 1.0D;
            double distance = fiddledDistance(biomeZoomSeed, sampleQuartX, sampleQuartY, sampleQuartZ,
                    sampleLocalX, sampleLocalY, sampleLocalZ);

            if (closestDistance > distance) {
                closestCorner = corner;
                closestDistance = distance;
            }
        }

        int sampleQuartX = (closestCorner & 4) == 0 ? quartX : quartX + 1;
        int sampleQuartY = (closestCorner & 2) == 0 ? quartY : quartY + 1;
        int sampleQuartZ = (closestCorner & 1) == 0 ? quartZ : quartZ + 1;
        return noiseBiomeNoWait(level, originChunk, sampleQuartX, sampleQuartY, sampleQuartZ);
    }

    private static Holder<Biome> noiseBiomeNoWait(ServerLevel level, LevelChunk originChunk, int quartX, int quartY, int quartZ) {
        ChunkPos originPos = originChunk.getPos();
        int chunkX = QuartPos.toSection(quartX);
        int chunkZ = QuartPos.toSection(quartZ);
        ChunkAccess chunk = chunkX == originPos.x && chunkZ == originPos.z
                ? originChunk
                : level.getChunkSource().getChunkNow(chunkX, chunkZ);
        return chunk != null ? chunk.getNoiseBiome(quartX, quartY, quartZ) : level.getUncachedNoiseBiome(quartX, quartY, quartZ);
    }

    private static double fiddledDistance(long seed, int x, int y, int z, double xNoise, double yNoise, double zNoise) {
        long mixed = LinearCongruentialGenerator.next(seed, (long)x);
        mixed = LinearCongruentialGenerator.next(mixed, (long)y);
        mixed = LinearCongruentialGenerator.next(mixed, (long)z);
        mixed = LinearCongruentialGenerator.next(mixed, (long)x);
        mixed = LinearCongruentialGenerator.next(mixed, (long)y);
        mixed = LinearCongruentialGenerator.next(mixed, (long)z);
        double xFiddle = fiddle(mixed);
        mixed = LinearCongruentialGenerator.next(mixed, seed);
        double yFiddle = fiddle(mixed);
        mixed = LinearCongruentialGenerator.next(mixed, seed);
        double zFiddle = fiddle(mixed);
        return Mth.square(zNoise + zFiddle) + Mth.square(yNoise + yFiddle) + Mth.square(xNoise + xFiddle);
    }

    private static double fiddle(long seed) {
        double value = (double)Math.floorMod(seed >> 24, 1024) / 1024.0D;
        return (value - 0.5D) * 0.9D;
    }

    private static void tryMeltDown(ServerLevel level, BlockPos.MutableBlockPos pos, int attempt) {
        if (attempt > 4 || level.random.nextFloat() < 0.3F) return;
        int yTopExclusive = pos.getY();
        int down = level.random.nextInt(attempt*8) + 1;
        pos.move(Direction.DOWN, down);
        if (!level.isLoaded(pos)) return;
        if (findFirstTopSnow(level, pos, yTopExclusive) && level.getBrightness(LightLayer.SKY, pos.above()) > 0)
            doMelt(level.getBlockState(pos), level, pos);
        else
            tryMeltDown(level, pos.setY(yTopExclusive - down), attempt + 1);
    }

    private static boolean isSnow(BlockState blockState) {
        return blockState.is(Blocks.SNOW);
    }

    private static boolean findFirstTopSnow(Level level, BlockPos.MutableBlockPos pos, int yTop) {
        while (pos.getY() < yTop && !isSnow(level.getBlockState(pos)))
            pos = pos.move(Direction.UP);
        if (pos.getY() == yTop) return false;
        pos.move(Direction.UP);
        while (isSnow(level.getBlockState(pos)))
            pos = pos.move(Direction.UP);
        pos.move(Direction.DOWN);
        return true;
    }

    public static void doMelt(BlockState state, ServerLevel level, BlockPos pos) {
        if (state.is(Blocks.SNOW)) {
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers <= 1) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            } else {
                level.setBlockAndUpdate(pos, state.setValue(SnowLayerBlock.LAYERS, layers - 1));
            }
        } else if (state.is(Blocks.ICE)) {
            ((IceBlock) Blocks.ICE).melt(state, level, pos);
        }
    }
}
