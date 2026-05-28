package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsConfig;
import com.judiraal.arcticnights.ArcticNightsFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

public final class ClimateCatchupQueue {
    private static final int RECENT_RESULTS = 128;
    private static final int VANILLA_PRECIPITATION_ATTEMPT_DIVISOR = 48;
    private static final int CLEAR_SAMPLE_BACKGROUND_OPERATIONS = 2;
    private static final int CHUNK_UNLOADED_RETRY_TICKS = 20;
    private static final String STATUS_COMPLETE = "complete";
    private static final String STATUS_PARTIAL = "partial";
    private static final String STATUS_PARTIAL_BUDGET_EXHAUSTED = "partial_budget_exhausted";
    private static final String STATUS_CHUNK_UNLOADED = "chunk_unloaded";
    private static final Map<ServerLevel, LevelQueue> QUEUES = new WeakHashMap<>();

    private ClimateCatchupQueue() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ArcticNightsFeatures.unloadedClimateCatchup()) {
            QUEUES.clear();
            return;
        }
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

        LevelQueue queue = QUEUES.get(level);
        long key = pos.toLong();
        if (queue != null && queue.pending.contains(key)) {
            debugUnloadRetainedPending(level, pos, dayTime, gameTime, queue);
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
        if (!ArcticNightsFeatures.unloadedClimateCatchup()) return 0;
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
        if (!ArcticNightsFeatures.unloadedClimateCatchup()) return new DebugSeedResult(0, fromDayTime, toDayTime);
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

    public static ProfileResult profile(ServerLevel level, ChunkPos pos) {
        if (!(level.getChunk(pos.x, pos.z, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) {
            return new ProfileResult(pos, false, 0, 0, 0, 0, 0.0F, "none");
        }

        CatchupDiagnostics diagnostics = new CatchupDiagnostics();
        ClimateSnapshot.WeatherState weather = history(level).currentWeather(level);
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
                ClimateSnapshot snapshot = ClimateService.sampleSnapshot(level, biome, surfacePos, exposedToSky, weather, dayTime);
                BlockPos target = snowTarget(level, surfacePos);
                Holder<Biome> targetBiome = level.getBiome(target);
                SurfaceCategory surfaceCategory = surfaceCategory(level, target);
                SurfaceCategory supportCategory = supportCategory(level, target);
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

                SkipReason failure = profileSnowFailure(level, target, targetBiome, snapshot, sampleBiomeKey, diagnostics);
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
        Job job = new Job(pos, cappedFrom, toDayTime, elapsed, forced, seed(level, pos, cappedFrom, toDayTime));
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

    private static void debugUnloadRetainedPending(ServerLevel level, ChunkPos pos, long dayTime, long gameTime, LevelQueue queue) {
        if (!ArcticNightsFeatures.unloadedClimateCatchupDebug()) return;
        ArcticNights.LOGGER.debug("Climate catch-up unload_retained chunk {},{} reason=pending_job day={} game={} queued={} pending={} history={}",
                pos.x, pos.z, dayTime, gameTime, queue.jobs.size(), queue.pending.size(), history(level).debugWindow(dayTime, dayTime));
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

    private static void debugJobStart(ServerLevel level, Job job, int samples, int precipitationSamples, long precipitationTicks, int randomTickSpeed, int precipitationOperationsBudget, int clearOperationsBudget, int operationsBudget, String operationsBySample, int maxOperations, int maxOperationsPerSlice, TickBudget budget, ClimateHistoryState history) {
        if (!ArcticNightsFeatures.unloadedClimateCatchupDebug()) return;
        ArcticNights.LOGGER.debug("Climate catch-up job_start chunk {},{} forced={} from={} to={} elapsed={} samples={} precipitationSamples={} precipitationTicks={} precipitationFraction={} randomTickSpeed={} attemptDivisor={} precipitationOperationsBudget={} clearOperationsBudget={} operationsBudget={} operationsBySample={} maxOperations={} maxOperationsPerSlice={} budgetRemaining={} history={}",
                job.pos().x, job.pos().z, job.forced(), job.fromDayTime(), job.toDayTime(), job.elapsedTicks(), samples,
                precipitationSamples, precipitationTicks, fractionSummary(precipitationTicks, Math.max(1L, job.toDayTime() - job.fromDayTime())),
                randomTickSpeed, VANILLA_PRECIPITATION_ATTEMPT_DIVISOR,
                precipitationOperationsBudget, clearOperationsBudget, operationsBudget, operationsBySample, maxOperations, maxOperationsPerSlice,
                budget.remaining(), history.debugWindow(job.fromDayTime(), job.toDayTime()));
    }

    private static String stampSummary(Optional<ClimateCatchupState.ChunkStamp> stamp) {
        return stamp.map(value -> "day=" + value.dayTime() + ",game=" + value.gameTime()).orElse("none");
    }

    private static void processQueue(ServerLevel level) {
        LevelQueue queue = queue(level);
        if (queue.jobs.isEmpty()) return;

        int jobs = Math.min(maxCatchupChunkSlicesThisTick(), queue.jobs.size());
        TickBudget budget = new TickBudget(ArcticNightsConfig.maxCatchupBlockChangesPerTick.get());
        ClimateCatchupState state = ClimateCatchupState.get(level);
        long gameTime = level.getGameTime();
        while (jobs-- > 0 && budget.hasRemaining() && !queue.jobs.isEmpty()) {
            Job job = queue.jobs.removeFirst();
            queue.pending.remove(job.pos().toLong());
            if (job.deferredUntilGameTime() > gameTime) {
                queue.jobs.addLast(job);
                queue.pending.add(job.pos().toLong());
                continue;
            }
            CatchupResult result = processJob(level, job, budget);
            queue.results.put(job.pos().toLong(), result);
            if (shouldRequeue(result)) {
                if (STATUS_CHUNK_UNLOADED.equals(result.status())) {
                    job.deferUntil(gameTime + CHUNK_UNLOADED_RETRY_TICKS);
                }
                queue.jobs.addLast(job);
                queue.pending.add(job.pos().toLong());
            } else if (shouldAdvanceStamp(result)) {
                state.mark(job.pos(), job.toDayTime(), gameTime);
            } else {
                debugStampRetained(level, job, result, queue);
            }
            if (ArcticNightsFeatures.unloadedClimateCatchupDebug()) {
                ArcticNights.LOGGER.debug("Climate catch-up job_result chunk {},{} status={} forced={} from={} to={} elapsed={} samples={} operations={} changed={} snow={} freeze={} melt={} budgetRemaining={} queued={} pending={} diagnostics={} history={}",
                        job.pos().x, job.pos().z, result.status(), job.forced(), job.fromDayTime(), job.toDayTime(),
                        result.elapsedTicks(), result.samples(), result.operations(), result.changed(), result.snow(),
                        result.frozen(), result.melted(), budget.remaining(), queue.jobs.size(), queue.pending.size(),
                        result.diagnostics(), history(level).debugWindow(job.fromDayTime(), job.toDayTime()));
            }
        }
    }

    private static int maxCatchupChunkSlicesThisTick() {
        return Math.max(1, ArcticNightsConfig.maxCatchupJobsPerTick.get());
    }

    private static int maxCatchupOperationsPerChunkSlice() {
        return Math.max(1, ArcticNightsConfig.maxCatchupOperationsPerChunkSlice.get());
    }

    private static boolean shouldRequeue(CatchupResult result) {
        return STATUS_PARTIAL.equals(result.status())
                || STATUS_PARTIAL_BUDGET_EXHAUSTED.equals(result.status())
                || STATUS_CHUNK_UNLOADED.equals(result.status());
    }

    private static boolean shouldAdvanceStamp(CatchupResult result) {
        return !STATUS_CHUNK_UNLOADED.equals(result.status());
    }

    private static void debugStampRetained(ServerLevel level, Job job, CatchupResult result, LevelQueue queue) {
        if (!ArcticNightsFeatures.unloadedClimateCatchupDebug()) return;
        ArcticNights.LOGGER.debug("Climate catch-up stamp_retained chunk {},{} status={} forced={} from={} to={} elapsed={} queued={} pending={} history={}",
                job.pos().x, job.pos().z, result.status(), job.forced(), job.fromDayTime(), job.toDayTime(), job.elapsedTicks(),
                queue.jobs.size(), queue.pending.size(), history(level).debugWindow(job.fromDayTime(), job.toDayTime()));
    }

    private static CatchupResult processJob(ServerLevel level, Job job, TickBudget budget) {
        if (!ArcticNightsFeatures.unloadedClimateCatchup()) {
            debugJobSkip(level, job, "disabled", budget);
            return CatchupResult.skipped(job, "disabled");
        }

        if (!(level.getChunk(job.pos().x, job.pos().z, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) {
            debugJobSkip(level, job, STATUS_CHUNK_UNLOADED, budget);
            return CatchupResult.skipped(job, STATUS_CHUNK_UNLOADED);
        }

        long elapsed = Math.max(1L, job.toDayTime() - job.fromDayTime());
        ClimateHistoryState history = history(level);
        if (!job.planned()) {
            int sampleTicks = Math.max(1, ArcticNightsConfig.catchupSampleTicks.get());
            int samples = Mth.clamp((int) Math.ceil(elapsed / (double) sampleTicks), 1, ArcticNightsConfig.maxCatchupSamplesPerChunk.get());
            int maxOperations = ArcticNightsConfig.maxCatchupOperationsPerChunk.get();
            PrecipitationPlan precipitationPlan = precipitationPlan(history, job.fromDayTime(), elapsed, samples);
            int randomTickSpeed = Math.max(0, level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING));
            OperationPlan operationPlan = operationPlan(precipitationPlan, elapsed, maxOperations, randomTickSpeed);
            job.diagnostics().recordPlan(elapsed, samples, precipitationPlan, operationPlan, maxOperations);
            job.plan(samples, operationPlan.operationsBySample(), maxOperations, operationPlan.operationsBudget(),
                    operationPlan.snowExposureAttemptsPerColumn(), operationPlan.precipitationOperationsBudget() > 0);
            debugJobStart(level, job, samples, precipitationPlan.precipitationSamples(), precipitationPlan.precipitationTicks(), randomTickSpeed,
                    operationPlan.precipitationOperationsBudget(), operationPlan.clearOperationsBudget(),
                    operationPlan.operationsBudget(), operationsBySampleSummary(operationPlan.operationsBySample()), maxOperations,
                    maxCatchupOperationsPerChunkSlice(), budget, history);
        }

        int maxOperationsPerSlice = Math.min(job.maxOperations(), maxCatchupOperationsPerChunkSlice());
        if (job.targetedSnow()) {
            return processTargetedSnowJob(level, chunk, job, budget, maxOperationsPerSlice);
        }

        int sliceOperations = 0;
        while (!job.complete() && sliceOperations < maxOperationsPerSlice && budget.hasRemaining()) {
            long previousSampleTime = job.sampleStartTime(elapsed);
            long sampleTime = job.sampleEndTime(elapsed);
            ClimateHistoryState.WeatherSample weather = history.weatherAt(sampleTime);
            boolean precipitationInWindow = history.hasPrecipitationBetween(previousSampleTime, sampleTime);
            int operationsThisSample = job.operationsThisSample();
            if (job.operationsInSample() >= operationsThisSample) {
                job.advanceSample();
                continue;
            }

            job.recordOperation();
            sliceOperations++;
            MutationResult result = sampleColumn(level, chunk, job, weather, precipitationInWindow, sampleTime, job.diagnostics());
            Mutation mutation = result.mutation();
            if (mutation == Mutation.NONE && result.skipReason() != null) {
                job.recordSkip(result.skipReason());
            }
            if (mutation != Mutation.NONE) {
                budget.consume();
                job.recordMutation(mutation);
            }

            if (job.operationsInSample() >= operationsThisSample) {
                job.advanceSample();
            }
        }

        String status = job.complete()
                ? STATUS_COMPLETE
                : (budget.hasRemaining() ? STATUS_PARTIAL : STATUS_PARTIAL_BUDGET_EXHAUSTED);
        return job.result(status);
    }

    private static CatchupResult processTargetedSnowJob(ServerLevel level, LevelChunk chunk, Job job, TickBudget budget, int maxOperationsPerSlice) {
        ClimateHistoryState history = history(level);
        ClimateHistoryState.WeatherSample weather = history.weatherAt(job.toDayTime());
        boolean precipitationInWindow = history.hasPrecipitationBetween(job.fromDayTime(), job.toDayTime());
        int sliceColumns = 0;
        while (!job.complete() && sliceColumns < maxOperationsPerSlice && budget.hasRemaining()) {
            int columnKey = job.targetColumnKey();
            job.recordOperation();
            sliceColumns++;
            MutationResult result = sampleTargetColumn(level, chunk, job, columnKey, weather, precipitationInWindow, job.toDayTime(), job.diagnostics());
            Mutation mutation = result.mutation();
            if (mutation == Mutation.NONE && result.skipReason() != null) {
                job.recordSkip(result.skipReason());
            }
            if (mutation != Mutation.NONE) {
                budget.consume();
                job.recordMutation(mutation);
            }
            job.advanceTargetColumn();
        }

        String status = job.complete()
                ? STATUS_COMPLETE
                : (budget.hasRemaining() ? STATUS_PARTIAL : STATUS_PARTIAL_BUDGET_EXHAUSTED);
        return job.result(status);
    }

    private static PrecipitationPlan precipitationPlan(ClimateHistoryState history, long fromDayTime, long elapsed, int samples) {
        long[] precipitationTicksBySample = new long[samples];
        long[] sampleTicksBySample = new long[samples];
        int precipitationSamples = 0;
        long precipitationTicks = 0L;
        long previousSampleTime = fromDayTime;
        for (int sample = 0; sample < samples; sample++) {
            long sampleTime = fromDayTime + Math.max(1L, elapsed * (sample + 1L) / samples);
            long sampleTicks = Math.max(1L, sampleTime - previousSampleTime);
            long samplePrecipitationTicks = Math.min(Math.max(0L, history.precipitationTicksBetween(previousSampleTime, sampleTime)), sampleTicks);
            sampleTicksBySample[sample] = sampleTicks;
            precipitationTicksBySample[sample] = samplePrecipitationTicks;
            if (samplePrecipitationTicks > 0L) {
                precipitationSamples++;
                precipitationTicks += samplePrecipitationTicks;
            }
            previousSampleTime = sampleTime;
        }
        return new PrecipitationPlan(precipitationSamples, Math.min(precipitationTicks, elapsed), precipitationTicksBySample, sampleTicksBySample);
    }

    private static OperationPlan operationPlan(PrecipitationPlan precipitationPlan, long elapsed, int maxOperations, int randomTickSpeed) {
        int samples = precipitationPlan.precipitationTicksBySample().length;
        int[] operationsBySample = new int[samples];
        if (precipitationPlan.precipitationTicks() <= 0L) {
            int operationsBudget = expectedWeatherOperations(elapsed, randomTickSpeed, maxOperations);
            distributeOperationsByWeight(operationsBySample, precipitationPlan.sampleTicksBySample(), operationsBudget);
            return new OperationPlan(operationsBySample, operationsBudget, 0, operationsBudget, randomTickSpeed, 0.0D);
        }

        int clearOperationsBudget = 0;
        for (int sample = 0; sample < samples; sample++) {
            if (precipitationPlan.precipitationTicksBySample()[sample] > 0L) continue;
            int expectedClearOperations = expectedWeatherOperations(precipitationPlan.sampleTicksBySample()[sample], randomTickSpeed, CLEAR_SAMPLE_BACKGROUND_OPERATIONS);
            operationsBySample[sample] = expectedClearOperations;
            clearOperationsBudget += expectedClearOperations;
        }

        int precipitationOperationsLimit = Math.max(0, maxOperations - clearOperationsBudget);
        int precipitationOperationsBudget = expectedWeatherOperations(precipitationPlan.precipitationTicks(), randomTickSpeed, precipitationOperationsLimit);
        distributeOperationsByWeight(operationsBySample, precipitationPlan.precipitationTicksBySample(), precipitationOperationsBudget);
        int operationsBudget = Math.min(maxOperations, clearOperationsBudget + precipitationOperationsBudget);
        double snowExposureAttemptsPerColumn = precipitationOperationsBudget / 256.0D;
        return new OperationPlan(operationsBySample, operationsBudget, precipitationOperationsBudget, clearOperationsBudget, randomTickSpeed, snowExposureAttemptsPerColumn);
    }

    private static int expectedWeatherOperations(long ticks, int randomTickSpeed, int maxOperations) {
        if (ticks <= 0L || randomTickSpeed <= 0 || maxOperations <= 0) return 0;
        long expected = Math.round(ticks * (double) randomTickSpeed / VANILLA_PRECIPITATION_ATTEMPT_DIVISOR);
        return Mth.clamp((int) Math.min(Integer.MAX_VALUE, expected), 0, maxOperations);
    }

    private static void distributeOperationsByWeight(int[] operationsBySample, long[] weightsBySample, int operationsBudget) {
        if (operationsBudget <= 0) return;
        long remainingWeight = 0L;
        int remainingSamples = 0;
        for (long weight : weightsBySample) {
            if (weight <= 0L) continue;
            remainingWeight += weight;
            remainingSamples++;
        }
        if (remainingWeight <= 0L || remainingSamples <= 0) return;

        int remainingOperations = operationsBudget;
        for (int sample = 0; sample < weightsBySample.length; sample++) {
            long weight = weightsBySample[sample];
            if (weight <= 0L) continue;
            int operations = (int) Math.round(remainingOperations * (weight / (double) Math.max(1L, remainingWeight)));
            operations = Mth.clamp(operations, 0, remainingOperations);
            operationsBySample[sample] += operations;
            remainingOperations -= operations;
            remainingWeight -= weight;
            remainingSamples--;
        }
    }

    private static String operationsBySampleSummary(int[] operationsBySample) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < operationsBySample.length; i++) {
            if (i > 0) builder.append('/');
            builder.append(operationsBySample[i]);
        }
        return builder.append(']').toString();
    }

    private static String fractionSummary(long numerator, long denominator) {
        return String.format(java.util.Locale.ROOT, "%.3f", numerator / (double) Math.max(1L, denominator));
    }

    private static MutationResult sampleColumn(ServerLevel level, LevelChunk chunk, Job job, ClimateHistoryState.WeatherSample weather, boolean precipitationInWindow, long sampleTime, CatchupDiagnostics diagnostics) {
        ChunkPos chunkPos = chunk.getPos();
        RandomSource random = job.random();
        int localX = random.nextInt(16);
        int localZ = random.nextInt(16);
        int columnKey = (localZ << 4) | localX;
        return sampleColumn(level, chunk, job, columnKey, weather, precipitationInWindow, sampleTime, diagnostics, true);
    }

    private static MutationResult sampleTargetColumn(ServerLevel level, LevelChunk chunk, Job job, int columnKey, ClimateHistoryState.WeatherSample weather, boolean precipitationInWindow, long sampleTime, CatchupDiagnostics diagnostics) {
        return sampleColumn(level, chunk, job, columnKey, weather, precipitationInWindow, sampleTime, diagnostics, false);
    }

    private static MutationResult sampleColumn(ServerLevel level, LevelChunk chunk, Job job, int columnKey, ClimateHistoryState.WeatherSample weather, boolean precipitationInWindow, long sampleTime, CatchupDiagnostics diagnostics, boolean allowFreeze) {
        ChunkPos chunkPos = chunk.getPos();
        int localX = columnKey & 15;
        int localZ = (columnKey >> 4) & 15;
        diagnostics.recordSampledColumn(columnKey);
        int x = chunkPos.getMinBlockX() + localX;
        int z = chunkPos.getMinBlockZ() + localZ;
        // ChunkAccess#getHeight returns the highest taken block; mutations target the air above it.
        BlockPos surfacePos = new BlockPos(x, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, localX, localZ) + 1, z);
        if (surfacePos.getY() < level.getMinBuildHeight() || surfacePos.getY() >= level.getMaxBuildHeight()) {
            return MutationResult.skipped(SkipReason.OUT_OF_WORLD);
        }

        Holder<Biome> biome = level.getBiome(surfacePos);
        String sampleBiomeKey = biomeKey(biome);
        diagnostics.recordBiome(sampleBiomeKey);
        boolean exposedToSky = ClimateService.isExposedToSky(level, surfacePos);
        ClimateSnapshot snapshot = ClimateService.sampleSnapshot(level, biome, surfacePos, exposedToSky, weather.state(), sampleTime);
        boolean inferredSnowfall = precipitationInWindow || (!weather.recorded() && shouldInferSnowfall(snapshot));
        diagnostics.recordWeather(weather.state(), weather.recorded(), precipitationInWindow, inferredSnowfall);
        if (tryMelt(level, surfacePos, snapshot)) return MutationResult.changed(Mutation.MELT);

        MutationResult snowResult = trySnow(level, surfacePos, snapshot, inferredSnowfall, job, diagnostics, sampleBiomeKey, columnKey);
        if (snowResult.mutation() == Mutation.SNOW) return snowResult;

        if (allowFreeze && tryFreeze(level, surfacePos, snapshot)) return MutationResult.changed(Mutation.FREEZE);
        return snowResult;
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

    private static MutationResult trySnow(ServerLevel level, BlockPos surfacePos, ClimateSnapshot snapshot, boolean inferredSnowfall, Job job, CatchupDiagnostics diagnostics, String sampleBiomeKey, int columnKey) {
        if (!inferredSnowfall && snapshot.weatherState() == ClimateSnapshot.WeatherState.CLEAR) {
            return MutationResult.skipped(SkipReason.NO_PRECIPITATION);
        }
        if (snapshot.precipitationKind() == ClimateSnapshot.PrecipitationKind.NONE) {
            diagnostics.recordNoBiomePrecipitationBiome(sampleBiomeKey);
            return MutationResult.skipped(SkipReason.NO_BIOME_PRECIPITATION);
        }

        float snowFactor = ClimateService.snowAccumulationFactor(snapshot.outdoorMinecraftTemperature());
        if (snowFactor <= 0.0F) {
            return MutationResult.skipped(SkipReason.TEMPERATURE_TOO_WARM);
        }
        diagnostics.recordSnowExposure(snowFactor);
        int targetLayers = targetSnowLayers(job, columnKey, snowFactor);
        diagnostics.recordSnowTarget(targetLayers);
        if (targetLayers <= 0) {
            return MutationResult.skipped(SkipReason.SNOW_TARGET_ZERO);
        }

        BlockPos target = snowTarget(level, surfacePos);
        Holder<Biome> biome = level.getBiome(target);
        diagnostics.recordTargetBiome(biomeKey(biome));
        SurfaceCategory surfaceCategory = surfaceCategory(level, target);
        SurfaceCategory supportCategory = supportCategory(level, target);
        diagnostics.recordSurface(surfaceCategory);
        diagnostics.recordSupportSurface(supportCategory);
        SkipReason surfaceFailure = snowSurfaceFailure(level, target, biome);
        if (surfaceFailure != null) {
            diagnostics.recordSurfaceFailure(surfaceCategory);
            diagnostics.recordSupportFailure(supportCategory);
            return MutationResult.skipped(surfaceFailure);
        }
        BlockState state = level.getBlockState(target);
        int cap = snowLayerCap();
        targetLayers = Mth.clamp(targetLayers, 1, cap);
        if (state.is(Blocks.SNOW)) {
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers >= targetLayers) return MutationResult.skipped(SkipReason.SNOW_TARGET_MET);
            level.setBlock(target, state.setValue(SnowLayerBlock.LAYERS, targetLayers), Block.UPDATE_ALL);
            diagnostics.recordSnowSurface(surfaceCategory);
            diagnostics.recordSnowMutation(SnowMutation.LAYER_GROWTH, columnKey);
            diagnostics.recordSnowDepth(targetLayers);
            return MutationResult.changed(Mutation.SNOW);
        }

        if (state.isAir() || SeasonalGroundCover.canBeBuriedBySnow(state)) {
            level.setBlock(target, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, targetLayers), Block.UPDATE_ALL);
            diagnostics.recordSnowSurface(surfaceCategory);
            diagnostics.recordSnowMutation(state.isAir() ? SnowMutation.NEW_SNOW : SnowMutation.BURIED_COVER, columnKey);
            diagnostics.recordSnowDepth(targetLayers);
            return MutationResult.changed(Mutation.SNOW);
        }
        diagnostics.recordSurfaceFailure(surfaceCategory);
        return MutationResult.skipped(SkipReason.NO_SNOW_SURFACE);
    }

    private static int targetSnowLayers(Job job, int columnKey, float snowFactor) {
        double expectedHits = Math.max(0.0D, job.snowExposureAttemptsPerColumn() * snowFactor);
        int target = 0;
        double threshold = 0.0D;
        int cap = snowLayerCap();
        for (int layer = 1; layer <= cap; layer++) {
            threshold += exponentialLayerThreshold(job.snowTargetSeed(), columnKey, layer);
            if (expectedHits < threshold) break;
            target = layer;
        }
        return target;
    }

    private static double exponentialLayerThreshold(long seed, int columnKey, int layer) {
        long value = seed
                ^ (long) columnKey * 0x9E3779B97F4A7C15L
                ^ (long) layer * 0xBF58476D1CE4E5B9L;
        return -Math.log(unitDouble(value));
    }

    private static double unitDouble(long value) {
        long bits = mix64(value) >>> 11;
        return (bits + 1.0D) * 0x1.0p-53D;
    }

    private static SkipReason snowSurfaceFailure(ServerLevel level, BlockPos target, Holder<Biome> biome) {
        if (target.getY() < level.getMinBuildHeight() || target.getY() >= level.getMaxBuildHeight()) {
            return SkipReason.OUT_OF_WORLD;
        }
        if (level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, target) >= 10) {
            return SkipReason.BLOCK_LIGHT;
        }

        BlockState state = level.getBlockState(target);
        if (state.is(Blocks.SNOW)) {
            if (state.getValue(SnowLayerBlock.LAYERS) >= snowLayerCap()) {
                return SkipReason.SNOW_LAYER_CAP;
            }
        } else if (!state.isAir() && !SeasonalGroundCover.canBeBuriedBySnow(state)) {
            return SkipReason.NO_SNOW_SURFACE;
        }

        if (!Blocks.SNOW.defaultBlockState().canSurvive(level, target)) {
            return SkipReason.SNOW_CANNOT_SURVIVE;
        }
        if (!biome.value().hasPrecipitation()) {
            return SkipReason.NO_BIOME_PRECIPITATION;
        }
        return null;
    }

    private static SkipReason profileSnowFailure(ServerLevel level, BlockPos target, Holder<Biome> targetBiome, ClimateSnapshot snapshot, String sampleBiomeKey, CatchupDiagnostics diagnostics) {
        if (snapshot.precipitationKind() == ClimateSnapshot.PrecipitationKind.NONE) {
            diagnostics.recordNoBiomePrecipitationBiome(sampleBiomeKey);
            return SkipReason.NO_BIOME_PRECIPITATION;
        }
        if (ClimateService.snowAccumulationFactor(snapshot.outdoorMinecraftTemperature()) <= 0.0F) {
            return SkipReason.TEMPERATURE_TOO_WARM;
        }
        return snowSurfaceFailure(level, target, targetBiome);
    }

    private static boolean shouldInferSnowfall(ClimateSnapshot snapshot) {
        return snapshot.precipitationKind() != ClimateSnapshot.PrecipitationKind.NONE
                && ClimateService.snowAccumulationFactor(snapshot.outdoorMinecraftTemperature()) > 0.0F
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

    private static SurfaceCategory supportCategory(ServerLevel level, BlockPos target) {
        return surfaceCategory(level, target.below());
    }

    private static long seed(ServerLevel level, ChunkPos pos, long fromDayTime, long toDayTime) {
        return mix64(level.getSeed()
                ^ pos.toLong() * 0x9E3779B97F4A7C15L
                ^ fromDayTime * 0xBF58476D1CE4E5B9L
                ^ toDayTime * 0x94D049BB133111EBL);
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

    private enum SkipReason {
        OUT_OF_WORLD("out_of_world"),
        NO_PRECIPITATION("no_precipitation"),
        NO_BIOME_PRECIPITATION("no_biome_precipitation"),
        TEMPERATURE_TOO_WARM("temperature_too_warm"),
        SNOW_FACTOR_ROLL("snow_factor_roll"),
        SNOW_TARGET_ZERO("snow_target_zero"),
        SNOW_TARGET_MET("snow_target_met"),
        BLOCK_LIGHT("block_light"),
        NO_SNOW_SURFACE("no_snow_surface"),
        SNOW_LAYER_CAP("snow_layer_cap"),
        SNOW_CANNOT_SURVIVE("snow_cannot_survive");

        private final String key;

        SkipReason(String key) {
            this.key = key;
        }
    }

    private enum SurfaceCategory {
        AIR("air"),
        SNOW("snow"),
        BURYABLE("buryable"),
        LEAVES("leaves"),
        WATER("water"),
        ICE("ice"),
        SOIL("soil"),
        STONE("stone"),
        SOLID("solid"),
        OTHER("other");

        private final String key;

        SurfaceCategory(String key) {
            this.key = key;
        }
    }

    private enum SnowMutation {
        NEW_SNOW("new"),
        BURIED_COVER("buried_cover"),
        LAYER_GROWTH("layer_growth");

        private final String key;

        SnowMutation(String key) {
            this.key = key;
        }
    }

    private record PrecipitationPlan(int precipitationSamples, long precipitationTicks, long[] precipitationTicksBySample, long[] sampleTicksBySample) {
    }

    private record OperationPlan(int[] operationsBySample, int operationsBudget, int precipitationOperationsBudget, int clearOperationsBudget, int randomTickSpeed, double snowExposureAttemptsPerColumn) {
    }

    private record MutationResult(Mutation mutation, SkipReason skipReason) {
        private static MutationResult changed(Mutation mutation) {
            return new MutationResult(mutation, null);
        }

        private static MutationResult skipped(SkipReason reason) {
            return new MutationResult(Mutation.NONE, reason);
        }
    }

    private static final class CatchupDiagnostics {
        private final EnumMap<SkipReason, Integer> skips = new EnumMap<>(SkipReason.class);
        private final EnumMap<SurfaceCategory, Integer> surfaces = new EnumMap<>(SurfaceCategory.class);
        private final EnumMap<SurfaceCategory, Integer> snowSurfaces = new EnumMap<>(SurfaceCategory.class);
        private final EnumMap<SurfaceCategory, Integer> surfaceFailures = new EnumMap<>(SurfaceCategory.class);
        private final EnumMap<SurfaceCategory, Integer> supportSurfaces = new EnumMap<>(SurfaceCategory.class);
        private final EnumMap<SurfaceCategory, Integer> supportFailures = new EnumMap<>(SurfaceCategory.class);
        private final EnumMap<SnowMutation, Integer> snowMutations = new EnumMap<>(SnowMutation.class);
        private final EnumMap<ClimateSnapshot.WeatherState, Integer> weatherStates = new EnumMap<>(ClimateSnapshot.WeatherState.class);
        private final int[] snowDepths = new int[9];
        private final Map<String, Integer> biomes = new HashMap<>();
        private final Map<String, Integer> targetBiomes = new HashMap<>();
        private final Map<String, Integer> noBiomePrecipitationBiomes = new HashMap<>();
        private final Set<Integer> sampledColumns = new HashSet<>();
        private final Set<Integer> snowChangedColumns = new HashSet<>();
        private final Set<Integer> snowLayerGrowthColumns = new HashSet<>();
        private long planElapsed;
        private long planPrecipitationTicks;
        private int planSamples;
        private int planOperationsBudget;
        private int planMaxOperations;
        private int planRandomTickSpeed;
        private double planSnowExposureAttemptsPerColumn;
        private boolean hasPlan;
        private int duplicateColumnSamples;
        private int recordedWeatherOperations;
        private int syntheticWeatherOperations;
        private int precipitationWindowOperations;
        private int snowfallOperations;
        private int snowFactorSamples;
        private float snowFactorTotal;
        private float snowFactorMin = Float.MAX_VALUE;
        private float snowFactorMax = Float.MIN_VALUE;
        private int snowTargetSamples;
        private int snowTargetTotal;
        private int snowTargetMin = Integer.MAX_VALUE;
        private int snowTargetMax;

        private void record(SkipReason reason) {
            skips.merge(reason, 1, Integer::sum);
        }

        private void recordPlan(long elapsed, int samples, PrecipitationPlan precipitationPlan, OperationPlan operationPlan, int maxOperations) {
            planElapsed = elapsed;
            planPrecipitationTicks = precipitationPlan.precipitationTicks();
            planSamples = samples;
            planOperationsBudget = operationPlan.operationsBudget();
            planMaxOperations = maxOperations;
            planRandomTickSpeed = operationPlan.randomTickSpeed();
            planSnowExposureAttemptsPerColumn = operationPlan.snowExposureAttemptsPerColumn();
            hasPlan = true;
        }

        private void recordSampledColumn(int columnKey) {
            if (!sampledColumns.add(columnKey)) duplicateColumnSamples++;
        }

        private void recordWeather(ClimateSnapshot.WeatherState weather, boolean recorded, boolean precipitationInWindow, boolean snowfall) {
            weatherStates.merge(weather, 1, Integer::sum);
            if (recorded) {
                recordedWeatherOperations++;
            } else {
                syntheticWeatherOperations++;
            }
            if (precipitationInWindow) precipitationWindowOperations++;
            if (snowfall) snowfallOperations++;
        }

        private void recordSnowExposure(float snowFactor) {
            snowFactorSamples++;
            snowFactorTotal += snowFactor;
            snowFactorMin = Math.min(snowFactorMin, snowFactor);
            snowFactorMax = Math.max(snowFactorMax, snowFactor);
        }

        private void recordSnowTarget(int layers) {
            snowTargetSamples++;
            snowTargetTotal += layers;
            snowTargetMin = Math.min(snowTargetMin, layers);
            snowTargetMax = Math.max(snowTargetMax, layers);
        }

        private void recordSurface(SurfaceCategory category) {
            surfaces.merge(category, 1, Integer::sum);
        }

        private void recordSnowSurface(SurfaceCategory category) {
            snowSurfaces.merge(category, 1, Integer::sum);
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

        private void recordSnowMutation(SnowMutation mutation, int columnKey) {
            snowMutations.merge(mutation, 1, Integer::sum);
            snowChangedColumns.add(columnKey);
            if (mutation == SnowMutation.LAYER_GROWTH) snowLayerGrowthColumns.add(columnKey);
        }

        private void recordSnowDepth(BlockState state) {
            if (state.is(Blocks.SNOW)) {
                recordSnowDepth(state.getValue(SnowLayerBlock.LAYERS));
            } else {
                recordSnowDepth(0);
            }
        }

        private void recordSnowDepth(int layers) {
            snowDepths[Mth.clamp(layers, 0, 8)]++;
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
            appendPlanSummary(builder);
            appendWeatherSummary(builder);
            appendSnowFactorSummary(builder);
            appendSnowTargetSummary(builder);
            appendColumnSummary(builder);
            appendEnumSummary(builder, "surfaces", surfaces);
            appendEnumSummary(builder, "snow_surfaces", snowSurfaces);
            appendEnumSummary(builder, "surface_failures", surfaceFailures);
            appendEnumSummary(builder, "support_surfaces", supportSurfaces);
            appendEnumSummary(builder, "support_failures", supportFailures);
            appendSnowMutationSummary(builder);
            appendSnowDepthSummary(builder);
            appendMapSummary(builder, "biomes", biomes, 3);
            appendMapSummary(builder, "target_biomes", targetBiomes, 3);
            appendMapSummary(builder, "no_biome_precip_biomes", noBiomePrecipitationBiomes, 3);
            return builder.isEmpty() ? "none" : builder.toString();
        }

        private void appendPlanSummary(StringBuilder builder) {
            if (!hasPlan) return;
            appendSectionPrefix(builder, "plan");
            builder.append("elapsed:").append(planElapsed)
                    .append("|precipTicks:").append(planPrecipitationTicks)
                    .append("|precipFrac:").append(decimal(planPrecipitationTicks / (float) Math.max(1L, planElapsed)))
                    .append("|randomTickSpeed:").append(planRandomTickSpeed)
                    .append("|attemptDivisor:").append(VANILLA_PRECIPITATION_ATTEMPT_DIVISOR)
                    .append("|ops:").append(planOperationsBudget)
                    .append("|maxOps:").append(planMaxOperations)
                    .append("|samples:").append(planSamples)
                    .append("|snowOpsPerColumn:").append(decimal(planSnowExposureAttemptsPerColumn));
        }

        private void appendWeatherSummary(StringBuilder builder) {
            if (weatherStates.isEmpty() && recordedWeatherOperations == 0 && syntheticWeatherOperations == 0) return;
            appendSectionPrefix(builder, "weather");
            boolean first = true;
            for (ClimateSnapshot.WeatherState weather : ClimateSnapshot.WeatherState.values()) {
                int count = weatherStates.getOrDefault(weather, 0);
                if (count <= 0) continue;
                if (!first) builder.append('|');
                builder.append(weather.name().toLowerCase(Locale.ROOT)).append(':').append(count);
                first = false;
            }
            builder.append("|recorded:").append(recordedWeatherOperations)
                    .append("|synthetic:").append(syntheticWeatherOperations)
                    .append("|precipWindow:").append(precipitationWindowOperations)
                    .append("|snowfall:").append(snowfallOperations);
        }

        private void appendSnowFactorSummary(StringBuilder builder) {
            if (snowFactorSamples <= 0) return;
            appendSectionPrefix(builder, "snow_factor");
            builder.append("samples:").append(snowFactorSamples)
                    .append("|avg:").append(decimal(snowFactorTotal / snowFactorSamples))
                    .append("|min:").append(decimal(snowFactorMin))
                    .append("|max:").append(decimal(snowFactorMax));
        }

        private void appendSnowTargetSummary(StringBuilder builder) {
            if (snowTargetSamples <= 0) return;
            appendSectionPrefix(builder, "snow_targets");
            builder.append("samples:").append(snowTargetSamples)
                    .append("|avg:").append(decimal(snowTargetTotal / (float) snowTargetSamples))
                    .append("|min:").append(snowTargetMin)
                    .append("|max:").append(snowTargetMax);
        }

        private void appendColumnSummary(StringBuilder builder) {
            if (sampledColumns.isEmpty() && snowChangedColumns.isEmpty()) return;
            appendSectionPrefix(builder, "columns");
            builder.append("sampled:").append(sampledColumns.size())
                    .append("|repeats:").append(duplicateColumnSamples)
                    .append("|snowChanged:").append(snowChangedColumns.size())
                    .append("|layerGrowth:").append(snowLayerGrowthColumns.size());
        }

        private static void appendEnumSummary(StringBuilder builder, String key, EnumMap<SurfaceCategory, Integer> values) {
            if (values.isEmpty()) return;
            appendSectionPrefix(builder, key);
            boolean first = true;
            for (SurfaceCategory category : SurfaceCategory.values()) {
                int count = values.getOrDefault(category, 0);
                if (count <= 0) continue;
                if (!first) builder.append('|');
                builder.append(category.key).append(':').append(count);
                first = false;
            }
        }

        private void appendSnowMutationSummary(StringBuilder builder) {
            if (snowMutations.isEmpty()) return;
            appendSectionPrefix(builder, "snow_mutations");
            boolean first = true;
            for (SnowMutation mutation : SnowMutation.values()) {
                int count = snowMutations.getOrDefault(mutation, 0);
                if (count <= 0) continue;
                if (!first) builder.append('|');
                builder.append(mutation.key).append(':').append(count);
                first = false;
            }
        }

        private void appendSnowDepthSummary(StringBuilder builder) {
            boolean hasDepth = false;
            for (int count : snowDepths) {
                if (count > 0) {
                    hasDepth = true;
                    break;
                }
            }
            if (!hasDepth) return;
            appendSectionPrefix(builder, "snow_depths");
            boolean first = true;
            for (int layers = 0; layers < snowDepths.length; layers++) {
                int count = snowDepths[layers];
                if (count <= 0) continue;
                if (!first) builder.append('|');
                builder.append(layers).append(':').append(count);
                first = false;
            }
        }

        private static void appendMapSummary(StringBuilder builder, String key, Map<String, Integer> values, int limit) {
            if (values.isEmpty()) return;
            appendSectionPrefix(builder, key);
            Set<String> used = new HashSet<>();
            for (int i = 0; i < limit; i++) {
                String bestKey = null;
                int bestCount = 0;
                for (Map.Entry<String, Integer> entry : values.entrySet()) {
                    if (used.contains(entry.getKey()) || entry.getValue() <= bestCount) continue;
                    bestKey = entry.getKey();
                    bestCount = entry.getValue();
                }
                if (bestKey == null) break;
                if (i > 0) builder.append('|');
                builder.append(bestKey).append(':').append(bestCount);
                used.add(bestKey);
            }
        }

        private static void appendSectionPrefix(StringBuilder builder, String key) {
            if (!builder.isEmpty()) builder.append(';');
            builder.append(key).append('=');
        }

        private static String decimal(double value) {
            return String.format(Locale.ROOT, "%.3f", value);
        }
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

    private static final class Job {
        private final ChunkPos pos;
        private final long fromDayTime;
        private final long toDayTime;
        private final long elapsedTicks;
        private final boolean forced;
        private final RandomSource random;
        private final long snowTargetSeed;
        private final CatchupDiagnostics diagnostics = new CatchupDiagnostics();
        private boolean planned;
        private int samples;
        private int[] operationsBySample;
        private int operationsBudget;
        private int maxOperations;
        private double snowExposureAttemptsPerColumn;
        private boolean targetedSnow;
        private int nextSample;
        private int nextTargetColumn;
        private int operationsInSample;
        private int operations;
        private int changed;
        private int snow;
        private int frozen;
        private int melted;
        private long deferredUntilGameTime;

        private Job(ChunkPos pos, long fromDayTime, long toDayTime, long elapsedTicks, boolean forced, long seed) {
            this.pos = pos;
            this.fromDayTime = fromDayTime;
            this.toDayTime = toDayTime;
            this.elapsedTicks = elapsedTicks;
            this.forced = forced;
            this.random = RandomSource.create(seed);
            this.snowTargetSeed = mix64(seed ^ 0xD1B54A32D192ED03L);
        }

        private ChunkPos pos() {
            return pos;
        }

        private long fromDayTime() {
            return fromDayTime;
        }

        private long toDayTime() {
            return toDayTime;
        }

        private long elapsedTicks() {
            return elapsedTicks;
        }

        private boolean forced() {
            return forced;
        }

        private RandomSource random() {
            return random;
        }

        private long snowTargetSeed() {
            return snowTargetSeed;
        }

        private double snowExposureAttemptsPerColumn() {
            return snowExposureAttemptsPerColumn;
        }

        private boolean targetedSnow() {
            return targetedSnow;
        }

        private boolean planned() {
            return planned;
        }

        private int maxOperations() {
            return maxOperations;
        }

        private CatchupDiagnostics diagnostics() {
            return diagnostics;
        }

        private long deferredUntilGameTime() {
            return deferredUntilGameTime;
        }

        private void deferUntil(long gameTime) {
            deferredUntilGameTime = gameTime;
        }

        private int operationsInSample() {
            return operationsInSample;
        }

        private void plan(int samples, int[] operationsBySample, int maxOperations, int operationsBudget, double snowExposureAttemptsPerColumn, boolean targetedSnow) {
            this.samples = samples;
            this.operationsBySample = operationsBySample;
            this.maxOperations = maxOperations;
            this.operationsBudget = operationsBudget;
            this.snowExposureAttemptsPerColumn = snowExposureAttemptsPerColumn;
            this.targetedSnow = targetedSnow;
            this.planned = true;
        }

        private long sampleStartTime(long elapsed) {
            if (nextSample <= 0) return fromDayTime;
            return fromDayTime + Math.max(1L, elapsed * nextSample / samples);
        }

        private long sampleEndTime(long elapsed) {
            return fromDayTime + Math.max(1L, elapsed * (nextSample + 1L) / samples);
        }

        private int operationsThisSample() {
            if (nextSample < 0 || nextSample >= operationsBySample.length) return 0;
            return operationsBySample[nextSample];
        }

        private void recordOperation() {
            operations++;
            operationsInSample++;
        }

        private void recordSkip(SkipReason reason) {
            diagnostics.record(reason);
        }

        private void recordMutation(Mutation mutation) {
            changed++;
            switch (mutation) {
                case SNOW -> snow++;
                case FREEZE -> frozen++;
                case MELT -> melted++;
                case NONE -> {
                }
            }
        }

        private void advanceSample() {
            nextSample++;
            operationsInSample = 0;
        }

        private int targetColumnKey() {
            int offset = (int) snowTargetSeed & 255;
            return (nextTargetColumn * 73 + offset) & 255;
        }

        private void advanceTargetColumn() {
            nextTargetColumn++;
        }

        private boolean complete() {
            if (targetedSnow) return planned && nextTargetColumn >= 256;
            return planned && (nextSample >= samples || operations >= operationsBudget || operations >= maxOperations);
        }

        private CatchupResult result(String status) {
            return new CatchupResult(pos, status, elapsedTicks, samples, operations, changed, snow, frozen, melted, forced, diagnostics.summary());
        }
    }

    public record QueueStats(int queued, int pending, int recentResults, int stampedChunks) {
    }

    public record InspectResult(ChunkPos pos, Optional<ClimateCatchupState.ChunkStamp> stamp, boolean pending, Optional<CatchupResult> lastResult) {
    }

    public record DebugSeedResult(int queued, long fromDayTime, long toDayTime) {
    }

    public record ProfileResult(ChunkPos pos, boolean loaded, int columns, int snowEligible, int snowPresent, int snowLayerTotal, float averageSnowFactor, String diagnostics) {
    }

    public record CatchupResult(ChunkPos pos, String status, long elapsedTicks, int samples, int operations, int changed, int snow, int frozen, int melted, boolean forced, String diagnostics) {
        private static CatchupResult skipped(Job job, String status) {
            return new CatchupResult(job.pos(), status, job.elapsedTicks(), 0, 0, 0, 0, 0, 0, job.forced(), "none");
        }
    }
}
