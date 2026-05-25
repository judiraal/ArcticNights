package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsConfig;
import com.judiraal.arcticnights.ArcticNightsFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

public final class ClimateCatchupQueue {
    private static final int RECENT_RESULTS = 128;
    private static final Map<ServerLevel, LevelQueue> QUEUES = new WeakHashMap<>();

    private ClimateCatchupQueue() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!shouldTrack(level)) continue;
            history(level).tick(level, ArcticNightsConfig.maxCatchupTicks.get());
            processQueue(level);
        }
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        ChunkPos pos = chunk.getPos();
        boolean newChunk = event.isNewChunk();
        runOnServerThread(level, () -> handleChunkLoad(level, pos, newChunk));
    }

    private static void handleChunkLoad(ServerLevel level, ChunkPos pos, boolean newChunk) {
        long dayTime = level.getDayTime();
        long gameTime = level.getGameTime();
        if (!ArcticNightsFeatures.unloadedClimateCatchup()) {
            debugIgnored(level, pos, "load", "disabled", newChunk, dayTime, gameTime);
            return;
        }
        if (!shouldTrack(level)) {
            debugIgnored(level, pos, "load", "not_tracked", newChunk, dayTime, gameTime);
            return;
        }

        ClimateCatchupState state = ClimateCatchupState.get(level);
        Optional<ClimateCatchupState.ChunkStamp> stamp = state.stamp(pos);
        debugLoad(level, pos, newChunk, stamp, dayTime, gameTime);
        if (newChunk) {
            if (!ArcticNightsFeatures.catchupNewChunksFromWorldStart()) {
                state.mark(pos, dayTime, level.getGameTime());
                debugMark(level, pos, "new_chunk_from_world_start_disabled", newChunk, dayTime, dayTime, 0L, gameTime);
                return;
            }
            state.mark(pos, 0L, gameTime);
            debugMark(level, pos, "new_chunk_retry_baseline", true, 0L, dayTime, dayTime, gameTime);
            enqueueOrMark(level, state, pos, 0L, dayTime, "new_chunk_from_world_start", true);
            return;
        }

        if (stamp.isEmpty()) {
            state.mark(pos, dayTime, level.getGameTime());
            debugMark(level, pos, "missing_stamp_existing_chunk", false, dayTime, dayTime, 0L, gameTime);
            return;
        }

        enqueueOrMark(level, state, pos, stamp.get().dayTime(), dayTime, "existing_stamp", false);
    }

    private static void enqueueOrMark(ServerLevel level, ClimateCatchupState state, ChunkPos pos, long fromDayTime, long dayTime, String reason, boolean newChunk) {
        long elapsed = dayTime - fromDayTime;
        if (elapsed < 0L) {
            state.mark(pos, dayTime, level.getGameTime());
            debugMark(level, pos, reason + ":negative_elapsed", newChunk, fromDayTime, dayTime, elapsed, level.getGameTime());
            return;
        }
        int minTicks = ArcticNightsConfig.minCatchupTicks.get();
        if (elapsed < minTicks) {
            state.mark(pos, dayTime, level.getGameTime());
            debugMark(level, pos, reason + ":elapsed_below_min:" + minTicks, newChunk, fromDayTime, dayTime, elapsed, level.getGameTime());
            return;
        }

        enqueue(level, state, pos, fromDayTime, dayTime, elapsed, false, reason, newChunk);
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        ChunkPos pos = chunk.getPos();
        runOnServerThread(level, () -> handleChunkUnload(level, pos));
    }

    private static void handleChunkUnload(ServerLevel level, ChunkPos pos) {
        long dayTime = level.getDayTime();
        long gameTime = level.getGameTime();
        if (!ArcticNightsFeatures.unloadedClimateCatchup()) {
            debugIgnored(level, pos, "unload", "disabled", false, dayTime, gameTime);
            return;
        }
        if (!shouldTrack(level)) {
            debugIgnored(level, pos, "unload", "not_tracked", false, dayTime, gameTime);
            return;
        }
        ClimateCatchupState.get(level).mark(pos, dayTime, gameTime);
        debugMark(level, pos, "chunk_unload", false, dayTime, dayTime, 0L, gameTime);
    }

    private static void runOnServerThread(ServerLevel level, Runnable action) {
        if (level.getServer().isSameThread()) {
            action.run();
        } else {
            level.getServer().execute(action);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        QUEUES.clear();
    }

    public static QueueStats stats(ServerLevel level) {
        LevelQueue queue = queue(level);
        return new QueueStats(queue.jobs.size(), queue.pending.size(), queue.results.size(), ClimateCatchupState.get(level).count());
    }

    public static InspectResult inspect(ServerLevel level, ChunkPos pos) {
        LevelQueue queue = queue(level);
        return new InspectResult(
                pos,
                ClimateCatchupState.get(level).stamp(pos),
                queue.pending.contains(pos.toLong()),
                Optional.ofNullable(queue.results.get(pos.toLong()))
        );
    }

    public static int enqueueDebug(ServerLevel level, ChunkPos center, int radius) {
        if (!shouldTrack(level)) return 0;
        ClimateCatchupState state = ClimateCatchupState.get(level);
        long toDayTime = level.getDayTime();
        long fallbackFrom = toDayTime - ArcticNightsConfig.maxCatchupTicks.get();
        int queued = 0;
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                ChunkPos pos = new ChunkPos(x, z);
                long fromDayTime = state.stamp(pos)
                        .map(ClimateCatchupState.ChunkStamp::dayTime)
                        .orElse(fallbackFrom);
                long elapsed = Math.max(1L, toDayTime - fromDayTime);
                if (enqueue(level, state, pos, fromDayTime, toDayTime, elapsed, true, "debug_command", false)) {
                    queued++;
                }
            }
        }
        return queued;
    }

    public static DebugSeedResult seedDebug(ServerLevel level, ChunkPos center, int radius, int ticks, ClimateSnapshot.WeatherState weather) {
        long toDayTime = level.getDayTime();
        long elapsed = Math.min(Math.max(1L, ticks), ArcticNightsConfig.maxCatchupTicks.get());
        long fromDayTime = Math.max(0L, toDayTime - elapsed);
        if (!shouldTrack(level)) return new DebugSeedResult(0, fromDayTime, toDayTime);

        history(level).replaceWithDebugSpan(fromDayTime, toDayTime, weather);
        reset(level, center, radius);
        debugSeed(level, center, radius, fromDayTime, toDayTime, weather);

        ClimateCatchupState state = ClimateCatchupState.get(level);
        int queued = 0;
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                ChunkPos pos = new ChunkPos(x, z);
                long chunkElapsed = Math.max(1L, toDayTime - fromDayTime);
                if (enqueue(level, state, pos, fromDayTime, toDayTime, chunkElapsed, true, "debug_seed", false)) {
                    queued++;
                }
            }
        }
        return new DebugSeedResult(queued, fromDayTime, toDayTime);
    }

    public static int reset(ServerLevel level, ChunkPos center, int radius) {
        LevelQueue queue = queue(level);
        int removedJobs = 0;
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                long key = ChunkPos.asLong(x, z);
                if (queue.pending.remove(key)) removedJobs++;
                queue.results.remove(key);
            }
        }
        queue.jobs.removeIf(job -> !queue.pending.contains(job.pos().toLong()));
        return ClimateCatchupState.get(level).resetAround(center, radius) + removedJobs;
    }

    private static boolean shouldTrack(ServerLevel level) {
        return ArcticNightsFeatures.climateSystem() && level.dimension() == Level.OVERWORLD && ArcticNights.appliesTo(level);
    }

    private static boolean enqueue(ServerLevel level, ClimateCatchupState state, ChunkPos pos, long fromDayTime, long toDayTime, long elapsed, boolean forced, String reason, boolean newChunk) {
        LevelQueue queue = queue(level);
        long key = pos.toLong();
        if (queue.pending.contains(key)) {
            debugSkipEnqueue(level, pos, reason, "already_pending", newChunk, fromDayTime, toDayTime, elapsed, forced, queue);
            return false;
        }

        while (queue.jobs.size() >= ArcticNightsConfig.maxCatchupQueuedChunks.get()) {
            Job dropped = queue.jobs.removeFirst();
            queue.pending.remove(dropped.pos().toLong());
            CatchupResult result = CatchupResult.skipped(dropped, "queue_overflow");
            queue.results.put(dropped.pos().toLong(), result);
            state.mark(dropped.pos(), dropped.toDayTime(), level.getGameTime());
            debugDrop(level, dropped, queue);
        }

        long cappedElapsed = Math.min(elapsed, ArcticNightsConfig.maxCatchupTicks.get());
        long cappedFrom = toDayTime - cappedElapsed;
        Job job = new Job(pos, cappedFrom, toDayTime, elapsed, forced);
        queue.jobs.addLast(job);
        queue.pending.add(key);
        debugQueued(level, job, reason, newChunk, fromDayTime, elapsed, cappedElapsed, queue);
        return true;
    }

    private static void debugIgnored(ServerLevel level, ChunkPos pos, String event, String reason, boolean newChunk, long dayTime, long gameTime) {
        if (!ArcticNightsFeatures.unloadedClimateCatchupDebug()) return;
        ArcticNights.LOGGER.debug("Climate catch-up {}_ignored chunk {},{} reason={} newChunk={} dimension={} day={} game={}",
                event, pos.x, pos.z, reason, newChunk, level.dimension().location(), dayTime, gameTime);
    }

    private static void debugLoad(ServerLevel level, ChunkPos pos, boolean newChunk, Optional<ClimateCatchupState.ChunkStamp> stamp, long dayTime, long gameTime) {
        if (!ArcticNightsFeatures.unloadedClimateCatchupDebug()) return;
        LevelQueue queue = QUEUES.get(level);
        ArcticNights.LOGGER.debug("Climate catch-up load chunk {},{} newChunk={} dimension={} day={} game={} stamp={} queued={} pending={} recent={}",
                pos.x, pos.z, newChunk, level.dimension().location(), dayTime, gameTime, stampSummary(stamp),
                queue == null ? 0 : queue.jobs.size(), queue == null ? 0 : queue.pending.size(), queue == null ? 0 : queue.results.size());
    }

    private static void debugMark(ServerLevel level, ChunkPos pos, String reason, boolean newChunk, long fromDayTime, long toDayTime, long elapsed, long gameTime) {
        if (!ArcticNightsFeatures.unloadedClimateCatchupDebug()) return;
        ArcticNights.LOGGER.debug("Climate catch-up mark chunk {},{} reason={} newChunk={} from={} to={} elapsed={} game={} history={}",
                pos.x, pos.z, reason, newChunk, fromDayTime, toDayTime, elapsed, gameTime, history(level).debugWindow(fromDayTime, toDayTime));
    }

    private static void debugSkipEnqueue(ServerLevel level, ChunkPos pos, String reason, String skipReason, boolean newChunk, long fromDayTime, long toDayTime, long elapsed, boolean forced, LevelQueue queue) {
        if (!ArcticNightsFeatures.unloadedClimateCatchupDebug()) return;
        ArcticNights.LOGGER.debug("Climate catch-up enqueue_skipped chunk {},{} reason={} skipReason={} newChunk={} forced={} from={} to={} elapsed={} queued={} pending={} history={}",
                pos.x, pos.z, reason, skipReason, newChunk, forced, fromDayTime, toDayTime, elapsed,
                queue.jobs.size(), queue.pending.size(), history(level).debugWindow(fromDayTime, toDayTime));
    }

    private static void debugDrop(ServerLevel level, Job dropped, LevelQueue queue) {
        if (!ArcticNightsFeatures.unloadedClimateCatchupDebug()) return;
        ArcticNights.LOGGER.debug("Climate catch-up queue_drop chunk {},{} reason=queue_overflow forced={} from={} to={} elapsed={} queued={} pending={} history={}",
                dropped.pos().x, dropped.pos().z, dropped.forced(), dropped.fromDayTime(), dropped.toDayTime(), dropped.elapsedTicks(),
                queue.jobs.size(), queue.pending.size(), history(level).debugWindow(dropped.fromDayTime(), dropped.toDayTime()));
    }

    private static void debugQueued(ServerLevel level, Job job, String reason, boolean newChunk, long requestedFrom, long requestedElapsed, long cappedElapsed, LevelQueue queue) {
        if (!ArcticNightsFeatures.unloadedClimateCatchupDebug()) return;
        ArcticNights.LOGGER.debug("Climate catch-up queued chunk {},{} reason={} newChunk={} forced={} requestedFrom={} from={} to={} elapsed={} cappedElapsed={} queued={} pending={} history={}",
                job.pos().x, job.pos().z, reason, newChunk, job.forced(), requestedFrom, job.fromDayTime(), job.toDayTime(),
                requestedElapsed, cappedElapsed, queue.jobs.size(), queue.pending.size(), history(level).debugWindow(job.fromDayTime(), job.toDayTime()));
    }

    private static void debugSeed(ServerLevel level, ChunkPos center, int radius, long fromDayTime, long toDayTime, ClimateSnapshot.WeatherState weather) {
        if (!ArcticNightsFeatures.unloadedClimateCatchupDebug()) return;
        ArcticNights.LOGGER.debug("Climate catch-up history_seed center={},{} radius={} weather={} from={} to={} history={}",
                center.x, center.z, radius, weather.name(), fromDayTime, toDayTime, history(level).debugWindow(fromDayTime, toDayTime));
    }

    private static void debugJobSkip(ServerLevel level, Job job, String reason, TickBudget budget) {
        if (!ArcticNightsFeatures.unloadedClimateCatchupDebug()) return;
        ArcticNights.LOGGER.debug("Climate catch-up job_skip chunk {},{} reason={} forced={} from={} to={} elapsed={} budgetRemaining={} history={}",
                job.pos().x, job.pos().z, reason, job.forced(), job.fromDayTime(), job.toDayTime(), job.elapsedTicks(),
                budget.remaining(), history(level).debugWindow(job.fromDayTime(), job.toDayTime()));
    }

    private static void debugJobStart(ServerLevel level, Job job, int samples, int operationsPerSample, int maxOperations, TickBudget budget, ClimateHistoryState history) {
        if (!ArcticNightsFeatures.unloadedClimateCatchupDebug()) return;
        ArcticNights.LOGGER.debug("Climate catch-up job_start chunk {},{} forced={} from={} to={} elapsed={} samples={} operationsPerSample={} maxOperations={} budgetRemaining={} history={}",
                job.pos().x, job.pos().z, job.forced(), job.fromDayTime(), job.toDayTime(), job.elapsedTicks(), samples,
                operationsPerSample, maxOperations, budget.remaining(), history.debugWindow(job.fromDayTime(), job.toDayTime()));
    }

    private static String stampSummary(Optional<ClimateCatchupState.ChunkStamp> stamp) {
        return stamp.map(value -> "day=" + value.dayTime() + ",game=" + value.gameTime()).orElse("none");
    }

    private static void processQueue(ServerLevel level) {
        LevelQueue queue = queue(level);
        if (queue.jobs.isEmpty()) return;

        int jobs = ArcticNightsConfig.maxCatchupJobsPerTick.get();
        TickBudget budget = new TickBudget(ArcticNightsConfig.maxCatchupBlockChangesPerTick.get());
        ClimateCatchupState state = ClimateCatchupState.get(level);
        while (jobs-- > 0 && budget.hasRemaining() && !queue.jobs.isEmpty()) {
            Job job = queue.jobs.removeFirst();
            queue.pending.remove(job.pos().toLong());
            CatchupResult result = processJob(level, job, budget);
            queue.results.put(job.pos().toLong(), result);
            if (shouldAdvanceStamp(result)) {
                state.mark(job.pos(), job.toDayTime(), level.getGameTime());
            } else {
                debugStampRetained(level, job, result, queue);
            }
            if (ArcticNightsFeatures.unloadedClimateCatchupDebug()) {
                ArcticNights.LOGGER.debug("Climate catch-up job_result chunk {},{} status={} forced={} from={} to={} elapsed={} samples={} operations={} changed={} snow={} freeze={} melt={} budgetRemaining={} queued={} pending={} history={}",
                        job.pos().x, job.pos().z, result.status(), job.forced(), job.fromDayTime(), job.toDayTime(),
                        result.elapsedTicks(), result.samples(), result.operations(), result.changed(), result.snow(),
                        result.frozen(), result.melted(), budget.remaining(), queue.jobs.size(), queue.pending.size(),
                        history(level).debugWindow(job.fromDayTime(), job.toDayTime()));
            }
        }
    }

    private static boolean shouldAdvanceStamp(CatchupResult result) {
        return !"chunk_unloaded".equals(result.status());
    }

    private static void debugStampRetained(ServerLevel level, Job job, CatchupResult result, LevelQueue queue) {
        if (!ArcticNightsFeatures.unloadedClimateCatchupDebug()) return;
        ArcticNights.LOGGER.debug("Climate catch-up stamp_retained chunk {},{} status={} forced={} from={} to={} elapsed={} queued={} pending={} history={}",
                job.pos().x, job.pos().z, result.status(), job.forced(), job.fromDayTime(), job.toDayTime(), job.elapsedTicks(),
                queue.jobs.size(), queue.pending.size(), history(level).debugWindow(job.fromDayTime(), job.toDayTime()));
    }

    private static CatchupResult processJob(ServerLevel level, Job job, TickBudget budget) {
        if (!job.forced() && !ArcticNightsFeatures.unloadedClimateCatchup()) {
            debugJobSkip(level, job, "disabled", budget);
            return CatchupResult.skipped(job, "disabled");
        }

        if (!(level.getChunk(job.pos().x, job.pos().z, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) {
            debugJobSkip(level, job, "chunk_unloaded", budget);
            return CatchupResult.skipped(job, "chunk_unloaded");
        }

        long elapsed = Math.max(1L, job.toDayTime() - job.fromDayTime());
        int sampleTicks = Math.max(1, ArcticNightsConfig.catchupSampleTicks.get());
        int samples = Mth.clamp((int) Math.ceil(elapsed / (double) sampleTicks), 1, ArcticNightsConfig.maxCatchupSamplesPerChunk.get());
        int maxOperations = ArcticNightsConfig.maxCatchupOperationsPerChunk.get();
        int operationsPerSample = Math.max(1, (maxOperations + samples - 1) / samples);
        RandomSource random = RandomSource.create(seed(level, job));
        ClimateHistoryState history = history(level);
        debugJobStart(level, job, samples, operationsPerSample, maxOperations, budget, history);

        int operations = 0;
        int changed = 0;
        int snow = 0;
        int frozen = 0;
        int melted = 0;
        long previousSampleTime = job.fromDayTime();
        for (int sample = 0; sample < samples && operations < maxOperations && budget.hasRemaining(); sample++) {
            long sampleTime = job.fromDayTime() + Math.max(1L, elapsed * (sample + 1L) / samples);
            ClimateHistoryState.WeatherSample weather = history.weatherAt(sampleTime);
            boolean precipitationInWindow = history.hasPrecipitationBetween(previousSampleTime, sampleTime);
            for (int op = 0; op < operationsPerSample && operations < maxOperations && budget.hasRemaining(); op++) {
                operations++;
                Mutation mutation = sampleColumn(level, chunk, random, weather, precipitationInWindow, sampleTime);
                if (mutation == Mutation.NONE) continue;

                budget.consume();
                changed++;
                switch (mutation) {
                    case SNOW -> snow++;
                    case FREEZE -> frozen++;
                    case MELT -> melted++;
                    case NONE -> {
                    }
                }
            }
            previousSampleTime = sampleTime;
        }

        String status = budget.hasRemaining() ? "complete" : "budget_exhausted";
        return new CatchupResult(job.pos(), status, job.elapsedTicks(), samples, operations, changed, snow, frozen, melted, job.forced());
    }

    private static Mutation sampleColumn(ServerLevel level, LevelChunk chunk, RandomSource random, ClimateHistoryState.WeatherSample weather, boolean precipitationInWindow, long sampleTime) {
        ChunkPos chunkPos = chunk.getPos();
        int localX = random.nextInt(16);
        int localZ = random.nextInt(16);
        int x = chunkPos.getMinBlockX() + localX;
        int z = chunkPos.getMinBlockZ() + localZ;
        // ChunkAccess#getHeight returns the highest taken block; mutations target the air above it.
        BlockPos surfacePos = new BlockPos(x, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, localX, localZ) + 1, z);
        if (surfacePos.getY() < level.getMinBuildHeight() || surfacePos.getY() >= level.getMaxBuildHeight()) {
            return Mutation.NONE;
        }

        Holder<Biome> biome = level.getBiome(surfacePos);
        boolean exposedToSky = ClimateService.isExposedToSky(level, surfacePos);
        ClimateSnapshot snapshot = ClimateService.sampleSnapshot(level, biome, surfacePos, exposedToSky, weather.state(), sampleTime);
        boolean inferredSnowfall = precipitationInWindow || (!weather.recorded() && shouldInferSnowfall(snapshot));
        if (tryMelt(level, surfacePos, snapshot)) return Mutation.MELT;
        if (trySnow(level, surfacePos, snapshot, inferredSnowfall)) return Mutation.SNOW;
        if (tryFreeze(level, surfacePos, snapshot)) return Mutation.FREEZE;
        return Mutation.NONE;
    }

    private static boolean tryMelt(ServerLevel level, BlockPos surfacePos, ClimateSnapshot snapshot) {
        if (!ArcticNightsConfig.catchupCanMeltIce.get()) return false;
        if (snapshot.snowBehavior() != ClimateSnapshot.SnowBehavior.MELTS) return false;

        BlockPos target = surfacePos;
        BlockState state = level.getBlockState(target);
        if (!isCatchupMeltable(state)) {
            target = surfacePos.below();
            state = level.getBlockState(target);
        }
        if (!isCatchupMeltable(state)) return false;

        SnowMeltHandler.doMelt(state, level, target);
        return !level.getBlockState(target).equals(state);
    }

    private static boolean isCatchupMeltable(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.ICE);
    }

    private static boolean trySnow(ServerLevel level, BlockPos surfacePos, ClimateSnapshot snapshot, boolean inferredSnowfall) {
        if (!inferredSnowfall && snapshot.weatherState() == ClimateSnapshot.WeatherState.CLEAR) return false;
        if (snapshot.precipitationKind() != ClimateSnapshot.PrecipitationKind.SNOW) return false;

        BlockPos target = snowTarget(level, surfacePos);
        Holder<Biome> biome = level.getBiome(target);
        if (!ClimateService.shouldSnow(level, biome, target, snapshot.outdoorMinecraftTemperature())) return false;

        BlockState state = level.getBlockState(target);
        int cap = snowLayerCap();
        if (state.is(Blocks.SNOW)) {
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers >= cap) return false;
            level.setBlock(target, state.setValue(SnowLayerBlock.LAYERS, layers + 1), Block.UPDATE_ALL);
            return true;
        }

        if (state.isAir() || SeasonalGroundCover.canBeBuriedBySnow(state)) {
            level.setBlock(target, Blocks.SNOW.defaultBlockState(), Block.UPDATE_ALL);
            return true;
        }
        return false;
    }

    private static boolean shouldInferSnowfall(ClimateSnapshot snapshot) {
        return snapshot.precipitationKind() == ClimateSnapshot.PrecipitationKind.SNOW
                && snapshot.snowBehavior() != ClimateSnapshot.SnowBehavior.MELTS;
    }

    private static BlockPos snowTarget(ServerLevel level, BlockPos surfacePos) {
        if (level.getBlockState(surfacePos).is(Blocks.SNOW)) return surfacePos;
        BlockPos below = surfacePos.below();
        if (level.getBlockState(below).is(Blocks.SNOW)) return below;
        return surfacePos;
    }

    private static int snowLayerCap() {
        int cap = Mth.clamp(ArcticNightsConfig.maxSnowLayersFromCatchup.get(), 1, 8);
        return ArcticNightsConfig.catchupCanCreateFullSnowBlocks.get() ? cap : Math.min(cap, 7);
    }

    private static boolean tryFreeze(ServerLevel level, BlockPos surfacePos, ClimateSnapshot snapshot) {
        if (!ArcticNightsConfig.catchupCanFreezeWater.get()) return false;
        BlockPos water = surfacePos.below();
        if (isChunkBorder(water)) return false;
        Holder<Biome> biome = level.getBiome(water);
        if (!ClimateService.shouldFreeze(level, biome, water, true, snapshot.outdoorMinecraftTemperature())) return false;
        level.setBlock(water, Blocks.ICE.defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    private static boolean isChunkBorder(BlockPos pos) {
        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;
        return localX == 0 || localX == 15 || localZ == 0 || localZ == 15;
    }

    private static long seed(ServerLevel level, Job job) {
        return mix64(level.getSeed()
                ^ job.pos().toLong() * 0x9E3779B97F4A7C15L
                ^ job.fromDayTime() * 0xBF58476D1CE4E5B9L
                ^ job.toDayTime() * 0x94D049BB133111EBL);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static LevelQueue queue(ServerLevel level) {
        return QUEUES.computeIfAbsent(level, ignored -> new LevelQueue());
    }

    private static ClimateHistoryState history(ServerLevel level) {
        return ClimateHistoryState.get(level);
    }

    private enum Mutation {
        NONE,
        SNOW,
        FREEZE,
        MELT
    }

    private static final class LevelQueue {
        private final ArrayDeque<Job> jobs = new ArrayDeque<>();
        private final Set<Long> pending = new HashSet<>();
        private final LinkedHashMap<Long, CatchupResult> results = new LinkedHashMap<>(32, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, CatchupResult> eldest) {
                return size() > RECENT_RESULTS;
            }
        };
    }

    private static final class TickBudget {
        private int remaining;

        private TickBudget(int remaining) {
            this.remaining = remaining;
        }

        private boolean hasRemaining() {
            return remaining > 0;
        }

        private int remaining() {
            return remaining;
        }

        private void consume() {
            remaining--;
        }
    }

    private record Job(ChunkPos pos, long fromDayTime, long toDayTime, long elapsedTicks, boolean forced) {
    }

    public record QueueStats(int queued, int pending, int recentResults, int stampedChunks) {
    }

    public record InspectResult(ChunkPos pos, Optional<ClimateCatchupState.ChunkStamp> stamp, boolean pending, Optional<CatchupResult> lastResult) {
    }

    public record DebugSeedResult(int queued, long fromDayTime, long toDayTime) {
    }

    public record CatchupResult(ChunkPos pos, String status, long elapsedTicks, int samples, int operations, int changed, int snow, int frozen, int melted, boolean forced) {
        private static CatchupResult skipped(Job job, String status) {
            return new CatchupResult(job.pos(), status, job.elapsedTicks(), 0, 0, 0, 0, 0, 0, job.forced());
        }
    }
}
