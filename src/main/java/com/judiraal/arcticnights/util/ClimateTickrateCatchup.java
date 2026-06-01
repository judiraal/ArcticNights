package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsAttachments;
import com.judiraal.arcticnights.ArcticNightsConfig;
import com.judiraal.arcticnights.ArcticNightsFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class ClimateTickrateCatchup {
    private static final int RECENT_RESULTS = 128;
    private static final double BURST_PRESSURE_SMOOTHING = 0.2D;
    private static final double BURST_SPIKE_HEADROOM = 1.25D;
    private static final double MIN_BURST_AGE_WEIGHT = 0.25D;
    private static final double MAX_BURST_AGE_WEIGHT = 4.0D;
    private static final double BURST_AGE_WEIGHT_POWER = 1.35D;
    private static final String SOURCE_STAMP_ELAPSED = "stamp_elapsed";
    private static final Map<ServerLevel, RuntimeState> RUNTIME = new WeakHashMap<>();
    private static final ThreadLocal<HistoricalClimateReplay> HISTORICAL_CLIMATE_REPLAY = new ThreadLocal<>();

    private ClimateTickrateCatchup() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ArcticNightsFeatures.tickrateClimateCatchup()) {
            RUNTIME.clear();
            return;
        }

        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!shouldTrack(level)) continue;
            int historyWindow = Math.max(ArcticNightsConfig.tickrateCatchupHistoryWindowTicks.get(),
                    ArcticNightsConfig.tickrateCatchupMaxDebtTicks.get());
            ClimateHistoryState history = ClimateHistoryState.get(level);
            history.tick(level, historyWindow);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        RUNTIME.clear();
    }

    public static void tickVanillaChunk(ServerLevel level, LevelChunk chunk, int baseRandomTickSpeed) {
        if (!shouldTrack(level)) {
            level.tickChunk(chunk, baseRandomTickSpeed);
            return;
        }

        RuntimeState runtime = runtime(level);
        runtime.beginTick(level.getGameTime());
        TickResult result = tickWithCatchupPlan(level, chunk, Math.max(0, baseRandomTickSpeed), runtime);
        if (result != null) {
            recordResult(runtime, result);
            debugResult(level, result, runtime);
        }
    }

    public static InspectResult inspect(ServerLevel level, ChunkPos pos) {
        Optional<LevelChunk> chunk = loadedChunk(level, pos);
        Optional<ClimateTickrateChunkStamp> stamp = chunk.flatMap(ClimateTickrateCatchup::stamp);
        RuntimeState runtime = runtime(level);
        long currentGameTime = level.getGameTime();
        long debt = stamp.map(value -> Math.max(Math.max(0L, currentGameTime - value.lastAppliedGameTime()),
                Math.max(value.pendingRandomTicks(), value.pendingReplayTicks()))).orElse(0L);
        int activeBurstTicks = stamp.map(ClimateTickrateChunkStamp::burstTicks).orElse(0);
        return new InspectResult(pos, chunk.isPresent(), stamp, debt, activeBurstTicks, Optional.ofNullable(runtime.results.get(pos.toLong())));
    }

    public static String historyDebug(ServerLevel level, long windowTicks) {
        long gameTime = level.getGameTime();
        long window = Math.max(1L, windowTicks);
        long fromGameTime = Math.max(0L, gameTime - window);
        return ClimateHistoryState.get(level).debugWindow(fromGameTime, gameTime);
    }

    public static int reset(ServerLevel level, ChunkPos center, int radius) {
        RuntimeState runtime = runtime(level);
        int reset = 0;
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                ChunkPos pos = new ChunkPos(x, z);
                Optional<LevelChunk> chunk = loadedChunk(level, pos);
                if (chunk.isPresent() && chunk.get().removeData(ArcticNightsAttachments.TICKRATE_CATCHUP.get()) != null) {
                    chunk.get().setUnsaved(true);
                    reset++;
                }
                runtime.results.remove(pos.toLong());
            }
        }
        return reset;
    }

    private static TickResult tickWithCatchupPlan(ServerLevel level, LevelChunk chunk, int baseRandomTickSpeed, RuntimeState runtime) {
        ChunkPos pos = chunk.getPos();
        long gameTime = level.getGameTime();
        long dayTime = level.getDayTime();
        int threshold = ArcticNightsConfig.tickrateCatchupDebtThresholdTicks.get();
        AttachmentType<ClimateTickrateChunkStamp> attachmentType = ArcticNightsAttachments.TICKRATE_CATCHUP.get();
        boolean newStamp = !chunk.hasData(attachmentType);
        ClimateTickrateChunkStamp stamp;
        ReplayPlan replayPlan = null;
        String source = SOURCE_STAMP_ELAPSED;
        if (newStamp) {
            replayPlan = newChunkReplayPlan(level, chunk, gameTime, dayTime);
            stamp = new ClimateTickrateChunkStamp(gameTime, gameTime, 0);
            stamp.setPendingPlan(replayPlan.randomTickDebtTicks(), replayPlan.randomWeatherState(),
                    replayPlan.randomDayTime(), replayPlan.replaySegments(),
                    replayPlan.historyStartGameTime(), replayPlan.historyEndGameTime());
            source = replayPlan.source("new");
        } else {
            stamp = chunk.getData(attachmentType);
            if (stamp.hasPendingReplay()) {
                source = "pending_" + stamp.pendingReplayAction().name().toLowerCase(java.util.Locale.ROOT);
            } else if (stamp.pendingRandomTicks() > 0L) {
                source = "pending_random";
            }
        }

        long lastApplied = stamp.lastAppliedGameTime();
        if (gameTime < lastApplied) {
            TickResult result = TickResult.reset(pos, "time_rewind", baseRandomTickSpeed);
            level.tickChunk(chunk, baseRandomTickSpeed);
            stamp.clearPendingWork();
            applyPlan(chunk, stamp, gameTime, gameTime, 0);
            return result;
        }

        long elapsed = gameTime - lastApplied;
        String preemptSourcePrefix = preemptionSourcePrefix(stamp);
        ReplayPlan preemptPlan = pendingWorkPreemptionPlan(level, chunk, stamp, gameTime, dayTime, threshold);
        if (preemptPlan != null) {
            stamp.setPendingPlan(preemptPlan.replayTicks(), preemptPlan.randomWeatherState(),
                    preemptPlan.randomDayTime(), preemptPlan.replaySegments(),
                    preemptPlan.historyStartGameTime(), preemptPlan.historyEndGameTime());
            source = preemptPlan.source(preemptSourcePrefix);
        }

        if (!stamp.hasPendingWork() && elapsed > threshold) {
            ReplayPlan elapsedReplayPlan = replayPlan != null
                    ? replayPlan
                    : elapsedReplayPlan(level, chunk, ClimateHistoryState.get(level), gameTime, dayTime, elapsed);
            if (elapsedReplayPlan.hasWork()) {
                stamp.setPendingPlan(elapsedReplayPlan.randomTickDebtTicks(), elapsedReplayPlan.randomWeatherState(),
                        elapsedReplayPlan.randomDayTime(), elapsedReplayPlan.replaySegments(),
                        elapsedReplayPlan.historyStartGameTime(), elapsedReplayPlan.historyEndGameTime());
            }
            source = elapsedReplayPlan.source("");
        }

        long randomDebt = stamp.pendingRandomTicks();
        long replayDebt = stamp.pendingReplayTicks();
        long debt = Math.max(randomDebt, replayDebt);
        if (debt <= threshold) {
            boolean shouldPersist = newStamp || debt >= Math.max(1, threshold);
            level.tickChunk(chunk, baseRandomTickSpeed);
            if (stamp.hasPendingWork()) {
                stamp.clearPendingWork();
                shouldPersist = true;
            } else if (elapsed > threshold) {
                shouldPersist = true;
            }
            if (shouldPersist) {
                applyPlan(chunk, stamp, gameTime, gameTime, 0);
            }
            return elapsed > threshold ? TickResult.skipped(pos, "no_action", 0L, baseRandomTickSpeed, 0L, source) : null;
        }

        if (stamp.burstTicks() >= ArcticNightsConfig.tickrateCatchupMaxBurstTicks.get()) {
            TickResult result = TickResult.skipped(pos, "max_duration", debt, baseRandomTickSpeed, 0L, source);
            level.tickChunk(chunk, baseRandomTickSpeed);
            applyPlan(chunk, stamp, gameTime, gameTime, 0);
            return result;
        }

        if (baseRandomTickSpeed <= 0) {
            TickResult result = TickResult.skipped(pos, "random_tick_disabled", debt, baseRandomTickSpeed, 0L, source);
            level.tickChunk(chunk, baseRandomTickSpeed);
            applyPlan(chunk, stamp, gameTime, gameTime, stamp.burstTicks());
            return result;
        }

        if (!runtime.shouldBurstThisTick(level, pos, stamp, gameTime)) {
            TickResult result = TickResult.skipped(pos, "fairness", debt, baseRandomTickSpeed, 0L, source);
            level.tickChunk(chunk, baseRandomTickSpeed);
            applyPlan(chunk, stamp, gameTime, gameTime, stamp.burstTicks());
            return result;
        }

        if (!runtime.claimBurstChunk()) {
            TickResult result = TickResult.skipped(pos, "budget", debt, baseRandomTickSpeed, 0L, source);
            level.tickChunk(chunk, baseRandomTickSpeed);
            applyPlan(chunk, stamp, gameTime, gameTime, stamp.burstTicks());
            return result;
        }

        ClimateTickrateChunkStamp.ReplaySegment replaySegment = stamp.pendingReplaySegment();
        long currentReplayTicks = replaySegment == null ? 0L : replaySegment.ticks();
        long burstDebt = Math.max(randomDebt, currentReplayTicks);
        long appliedTicks = multiplier(burstDebt);
        if (currentReplayTicks > 0L) {
            appliedTicks = Math.min(appliedTicks, currentReplayTicks);
        }
        int multiplier = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, appliedTicks));
        int effectiveRandomTickSpeed = effectiveRandomTickSpeed(baseRandomTickSpeed, multiplier);
        int burstTicks = stamp.burstTicks() + 1;
        Exposure exposure = Exposure.pending(stamp, replaySegment);
        TickResult result = TickResult.burst(pos, debt, multiplier, baseRandomTickSpeed, effectiveRandomTickSpeed,
                appliedTicks, burstTicks, exposure, source);
        runWithHistoricalClimateReplay(level, result, () -> level.tickChunk(chunk, effectiveRandomTickSpeed));
        MachineCatchupBridge.tickSupportedBlockEntities(level, chunk, result);
        stamp.consumePendingWork(appliedTicks);
        if (!stamp.hasPendingWork()) {
            burstTicks = 0;
        }
        applyPlan(chunk, stamp, gameTime, gameTime, burstTicks, gameTime);
        return result;
    }

    public static boolean isReplayingHistoricalPrecipitation(ServerLevel level) {
        HistoricalClimateReplay replay = historicalReplay(level);
        return replay != null && replay.action() == ClimateTickrateChunkStamp.ReplayAction.SNOW;
    }

    public static boolean isReplayingHistoricalMelt(ServerLevel level) {
        HistoricalClimateReplay replay = historicalReplay(level);
        return replay != null && replay.action() == ClimateTickrateChunkStamp.ReplayAction.MELT;
    }

    public static boolean isReplayingHistoricalClimate(ServerLevel level) {
        return historicalReplay(level) != null;
    }

    public static long historicalReplayDayTime(ServerLevel level) {
        HistoricalClimateReplay replay = historicalReplay(level);
        return replay == null ? level.getDayTime() : replay.dayTime();
    }

    public static float historicalOutdoorMinecraftTemperature(ServerLevel level, Holder<Biome> biome, BlockPos pos, boolean exposedToSky) {
        HistoricalClimateReplay replay = historicalReplay(level);
        if (replay == null) return ClimateService.outdoorMinecraftTemperature(level, biome, pos, exposedToSky);
        return ClimateService.sampleSnapshot(level, biome, pos, exposedToSky, replay.weatherState(), replay.dayTime())
                .outdoorMinecraftTemperature();
    }

    public static ClimateSnapshot.SnowBehavior historicalSnowBehavior(ServerLevel level, Holder<Biome> biome, BlockPos pos, boolean exposedToSky) {
        HistoricalClimateReplay replay = historicalReplay(level);
        if (replay == null) return ClimateService.snowBehaviorAt(level, biome, pos);
        return ClimateService.sampleSnapshot(level, biome, pos, exposedToSky, replay.weatherState(), replay.dayTime())
                .snowBehavior();
    }

    private static HistoricalClimateReplay historicalReplay(ServerLevel level) {
        HistoricalClimateReplay replay = HISTORICAL_CLIMATE_REPLAY.get();
        return replay != null && replay.level() == level ? replay : null;
    }

    private static void runWithHistoricalClimateReplay(ServerLevel level, TickResult result, Runnable task) {
        HistoricalClimateReplay replay = replayFor(level, result);
        if (replay == null) {
            task.run();
            return;
        }

        HistoricalClimateReplay previous = HISTORICAL_CLIMATE_REPLAY.get();
        HISTORICAL_CLIMATE_REPLAY.set(replay);
        try {
            task.run();
        } finally {
            if (previous == null) {
                HISTORICAL_CLIMATE_REPLAY.remove();
            } else {
                HISTORICAL_CLIMATE_REPLAY.set(previous);
            }
        }
    }

    private static HistoricalClimateReplay replayFor(ServerLevel level, TickResult result) {
        ClimateTickrateChunkStamp.ReplayAction action = result.replayAction();
        if (result.appliedTicks() <= 0L || result.historyWindowTicks() <= 0L) return null;
        return new HistoricalClimateReplay(level, action, result.replayWeatherState(), result.replayDayTime());
    }

    private static void applyPlan(LevelChunk chunk, ClimateTickrateChunkStamp stamp, long lastAppliedGameTime, long lastSeenGameTime, int burstTicks) {
        applyPlan(chunk, stamp, lastAppliedGameTime, lastSeenGameTime, burstTicks, stamp.lastBurstGameTime());
    }

    private static void applyPlan(LevelChunk chunk, ClimateTickrateChunkStamp stamp, long lastAppliedGameTime, long lastSeenGameTime, int burstTicks, long lastBurstGameTime) {
        stamp.mark(lastAppliedGameTime, lastSeenGameTime, burstTicks);
        stamp.markBurst(lastBurstGameTime);
        chunk.setData(ArcticNightsAttachments.TICKRATE_CATCHUP.get(), stamp);
        chunk.setUnsaved(true);
    }

    private static Optional<ClimateTickrateChunkStamp> stamp(LevelChunk chunk) {
        return chunk.getExistingData(ArcticNightsAttachments.TICKRATE_CATCHUP);
    }

    private static Optional<LevelChunk> loadedChunk(ServerLevel level, ChunkPos pos) {
        ChunkAccess chunk = level.getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
        return chunk instanceof LevelChunk levelChunk ? Optional.of(levelChunk) : Optional.empty();
    }

    private static int multiplier(long debt) {
        int targetTicks = Math.max(1, ArcticNightsConfig.tickrateCatchupTargetTicks.get());
        int raw = (int) Math.ceil(debt / (double) targetTicks);
        if (debt > ArcticNightsConfig.tickrateCatchupDebtThresholdTicks.get()) {
            raw = Math.max(2, raw);
        }
        return Mth.clamp(raw, 1, ArcticNightsConfig.tickrateCatchupMaxMultiplier.get());
    }

    private static int effectiveRandomTickSpeed(int baseRandomTickSpeed, int multiplier) {
        long effective = (long) Math.max(0, baseRandomTickSpeed) * Math.max(1, multiplier);
        return (int) Math.min(Integer.MAX_VALUE, effective);
    }

    private static ReplayPlan newChunkReplayPlan(ServerLevel level, LevelChunk chunk, long gameTime, long dayTime) {
        if (!ArcticNightsConfig.tickrateCatchupNewChunksUseRecentExposure.get()) {
            return ReplayPlan.zero("disabled");
        }

        long maxWindow = Math.min(gameTime, ArcticNightsConfig.tickrateCatchupHistoryWindowTicks.get());
        long window = Math.max(0L, maxWindow);
        if (window <= 0L) {
            return ReplayPlan.zero("no_history");
        }

        long fromGameTime = Math.max(0L, gameTime - window);
        return replayPlanBetween(level, chunk, ClimateHistoryState.get(level), fromGameTime, gameTime, dayTime, "history");
    }

    private static ReplayPlan elapsedReplayPlan(ServerLevel level, LevelChunk chunk, ClimateHistoryState history, long gameTime, long dayTime, long elapsed) {
        long window = Math.min(Math.min(elapsed, ArcticNightsConfig.tickrateCatchupHistoryWindowTicks.get()), Math.max(1L, gameTime));
        long fromGameTime = Math.max(0L, gameTime - window);
        return replayPlanBetween(level, chunk, history, fromGameTime, gameTime, dayTime, "history");
    }

    private static ReplayPlan neutralRandomPreemptionPlan(ServerLevel level, LevelChunk chunk, ClimateTickrateChunkStamp stamp,
                                                         long gameTime, long dayTime, int threshold) {
        if (stamp.hasPendingReplay()) return null;
        if (stamp.pendingRandomTicks() <= 0L) return null;
        if (stamp.pendingRandomWeather() != ClimateSnapshot.WeatherState.CLEAR) return null;

        long fromGameTime = preemptionFromGameTime(stamp, gameTime);
        if (gameTime - fromGameTime <= threshold) return null;

        ReplayPlan plan = replayPlanBetween(level, chunk, ClimateHistoryState.get(level), fromGameTime, gameTime, dayTime, "history");
        return plan.replayTicks() > threshold ? plan : null;
    }

    private static ReplayPlan pendingWorkPreemptionPlan(ServerLevel level, LevelChunk chunk, ClimateTickrateChunkStamp stamp,
                                                        long gameTime, long dayTime, int threshold) {
        if (!stamp.hasPendingReplay()) {
            return neutralRandomPreemptionPlan(level, chunk, stamp, gameTime, dayTime, threshold);
        }

        ClimateTickrateChunkStamp.ReplayAction pendingAction = stamp.pendingReplayAction();
        if (pendingAction == ClimateTickrateChunkStamp.ReplayAction.NONE) return null;

        long fromGameTime = preemptionFromGameTime(stamp, gameTime);
        if (gameTime - fromGameTime <= threshold) return null;

        ReplayPlan plan = replayPlanBetween(level, chunk, ClimateHistoryState.get(level), fromGameTime, gameTime, dayTime, "history");
        ClimateTickrateChunkStamp.ReplayAction newAction = plan.firstReplayAction();
        if (newAction == ClimateTickrateChunkStamp.ReplayAction.NONE || newAction == pendingAction) return null;
        return plan.replayTicks(newAction) > threshold ? plan : null;
    }

    private static long preemptionFromGameTime(ClimateTickrateChunkStamp stamp, long gameTime) {
        long historyWindow = Math.max(1L, ArcticNightsConfig.tickrateCatchupHistoryWindowTicks.get());
        long pendingTicks = Math.max(stamp.pendingRandomTicks(), stamp.pendingReplayTicks());
        long fallbackWindow = Math.min(Math.max(0L, pendingTicks), Math.min(gameTime, historyWindow));
        long fromGameTime = stamp.pendingHistoryEndGameTime() > 0L
                ? stamp.pendingHistoryEndGameTime()
                : Math.max(0L, gameTime - fallbackWindow);
        return Math.max(fromGameTime, Math.max(0L, gameTime - historyWindow));
    }

    private static String preemptionSourcePrefix(ClimateTickrateChunkStamp stamp) {
        ClimateTickrateChunkStamp.ReplayAction action = stamp.pendingReplayAction();
        if (action == ClimateTickrateChunkStamp.ReplayAction.NONE) {
            return "preempt_random";
        }
        return "preempt_" + action.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static ReplayPlan replayPlanBetween(ServerLevel level, LevelChunk chunk, ClimateHistoryState history, long fromGameTime, long toGameTime, long toDayTime, String source) {
        long from = Math.min(fromGameTime, toGameTime);
        long to = Math.max(fromGameTime, toGameTime);
        long window = Math.max(0L, to - from);
        if (window <= 0L) return ReplayPlan.zero(source);

        LocalClimateSamples samples = LocalClimateSamples.of(level, chunk);
        ReplayPlanBuilder builder = new ReplayPlanBuilder(window, source, toDayTime, from, to);
        long[] cursor = {from};
        history.visitSpansBetween(from, to, (start, end, sampleDayTime, weatherState) -> {
            if (start > cursor[0]) {
                addWeatherSpanToReplayPlan(level, samples, builder, cursor[0], start,
                        estimateDayTime(toGameTime, toDayTime, cursor[0], start), ClimateSnapshot.WeatherState.CLEAR);
            }
            long spanStart = Math.max(start, cursor[0]);
            if (end > spanStart) {
                addWeatherSpanToReplayPlan(level, samples, builder, spanStart, end, sampleDayTime, weatherState);
            }
            cursor[0] = Math.max(cursor[0], end);
        });
        if (cursor[0] < to) {
            addWeatherSpanToReplayPlan(level, samples, builder, cursor[0], to,
                    estimateDayTime(toGameTime, toDayTime, cursor[0], to), ClimateSnapshot.WeatherState.CLEAR);
        }
        return builder.finish();
    }

    private static void addWeatherSpanToReplayPlan(ServerLevel level, LocalClimateSamples samples, ReplayPlanBuilder builder,
                                                   long startGameTime, long endGameTime, long sampleDayTime, ClimateSnapshot.WeatherState weatherState) {
        long duration = Math.max(0L, endGameTime - startGameTime);
        if (duration <= 0L) return;
        builder.addRandomSpan(duration, sampleDayTime, weatherState);

        double snowFactor = 0.0D;
        double meltFactor = 0.0D;
        for (LocalClimateSample sample : samples.samples()) {
            ClimateSnapshot snapshot = ClimateService.sampleSnapshot(level, sample.biome(), sample.pos(), true, weatherState, sampleDayTime);
            double sampleSnowFactor = weatherState == ClimateSnapshot.WeatherState.CLEAR
                    ? 0.0D
                    : ClimateService.snowAccumulationFactor(snapshot.outdoorMinecraftTemperature());
            double sampleMeltFactor = 1.0D - ClimateService.snowAccumulationFactor(snapshot.outdoorMinecraftTemperature());

            if (snapshot.precipitationKind() == ClimateSnapshot.PrecipitationKind.NONE) {
                sampleSnowFactor = 0.0D;
                if (weatherState != ClimateSnapshot.WeatherState.CLEAR) sampleMeltFactor = 0.0D;
            }

            snowFactor += sampleSnowFactor;
            meltFactor += sampleMeltFactor;
        }
        snowFactor /= samples.size();
        meltFactor /= samples.size();

        long snowTicks = Math.round(duration * clamp(snowFactor, 0.0D, 1.0D));
        long meltTicks = Math.round(duration * clamp(meltFactor, 0.0D, 1.0D));
        if (snowTicks + meltTicks > duration) {
            long overflow = snowTicks + meltTicks - duration;
            if (meltTicks >= overflow) {
                meltTicks -= overflow;
            } else {
                snowTicks = Math.max(0L, snowTicks - (overflow - meltTicks));
                meltTicks = 0L;
            }
        }

        long actionTicks = Math.abs(snowTicks - meltTicks);
        if (snowTicks > meltTicks) {
            builder.addAction(ClimateTickrateChunkStamp.ReplayAction.SNOW, actionTicks, sampleDayTime, weatherState);
        } else if (meltTicks > snowTicks) {
            builder.addAction(ClimateTickrateChunkStamp.ReplayAction.MELT, actionTicks, sampleDayTime, weatherState);
        }
    }

    private static long estimateDayTime(long toGameTime, long toDayTime, long startGameTime, long endGameTime) {
        long midpoint = startGameTime + Math.max(0L, endGameTime - startGameTime) / 2L;
        return Math.max(0L, toDayTime - Math.max(0L, toGameTime - midpoint));
    }

    private static void recordResult(RuntimeState runtime, TickResult result) {
        runtime.results.put(result.pos().toLong(), result);
        while (runtime.results.size() > RECENT_RESULTS) {
            Long first = runtime.results.keySet().iterator().next();
            runtime.results.remove(first);
        }
    }

    private static void debugResult(ServerLevel level, TickResult result, RuntimeState runtime) {
        if (!ArcticNightsFeatures.tickrateClimateCatchupDiagnostics()) return;
        boolean notable = result.burstTicks() <= 1
                || result.burstTicks() % 20 == 0
                || !"burst".equals(result.status());
        if (!notable) return;
        ArcticNights.LOGGER.debug("Climate tick-rate catch-up chunk {},{} status={} source={} action={} debt={} multiplier={} baseRandomTickSpeed={} effectiveRandomTickSpeed={} appliedTicks={} burstTicks={} window={} snowTicks={} meltTicks={} neutralTicks={} snowFraction={} meltFraction={} replayDay={} replayWeather={} activeBursts={} recent={}",
                result.pos().x, result.pos().z, result.status(), result.source(), result.replayAction(), result.debtTicks(), result.multiplier(),
                result.baseRandomTickSpeed(), result.effectiveRandomTickSpeed(), result.appliedTicks(), result.burstTicks(),
                result.historyWindowTicks(), result.snowTicks(), result.meltTicks(), result.neutralTicks(),
                format3(result.snowFraction()), format3(result.meltFraction()), result.replayDayTime(), result.replayWeatherState(),
                runtime.burstChunksThisTick, runtime.results.size());
    }

    private static RuntimeState runtime(ServerLevel level) {
        return RUNTIME.computeIfAbsent(level, ignored -> new RuntimeState());
    }

    private static boolean shouldTrack(ServerLevel level) {
        return ArcticNightsFeatures.tickrateClimateCatchup() && level.dimension() == Level.OVERWORLD && ArcticNights.appliesTo(level);
    }

    private static int targetBurstChunksPerTick() {
        return Math.max(1, ArcticNightsConfig.tickrateCatchupMaxBurstChunksPerTick.get());
    }

    private static int emergencyBurstChunksPerTick() {
        int target = targetBurstChunksPerTick();
        return Math.max(target, (int) Math.ceil(target * BURST_SPIKE_HEADROOM));
    }

    private static double burstAgeWeight(ClimateTickrateChunkStamp stamp, long gameTime, double expectedIntervalTicks) {
        long lastBurst = Math.max(0L, Math.min(stamp.lastBurstGameTime(), gameTime));
        double ticksSinceLastBurst = Math.max(0L, gameTime - lastBurst);
        double ageRatio = ticksSinceLastBurst / Math.max(1.0D, expectedIntervalTicks);
        return clamp(Math.pow(ageRatio, BURST_AGE_WEIGHT_POWER), MIN_BURST_AGE_WEIGHT, MAX_BURST_AGE_WEIGHT);
    }

    private static double deterministicRoll(ServerLevel level, ChunkPos pos, long gameTime) {
        long value = level.getSeed() ^ pos.toLong() ^ Long.rotateLeft(gameTime * 0x9E3779B97F4A7C15L, 17);
        long mixed = mix64(value);
        return (mixed >>> 11) * 0x1.0p-53;
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double lerp(double current, double target) {
        return current + (target - current) * BURST_PRESSURE_SMOOTHING;
    }

    private static String format3(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private record LocalClimateSamples(List<LocalClimateSample> samples) {
        private static final int[][] SAMPLE_COLUMNS = {
                {8, 8},
                {3, 3},
                {12, 3},
                {3, 12},
                {12, 12}
        };

        private static LocalClimateSamples of(ServerLevel level, LevelChunk chunk) {
            ChunkPos chunkPos = chunk.getPos();
            ArrayList<LocalClimateSample> samples = new ArrayList<>(SAMPLE_COLUMNS.length);
            for (int[] column : SAMPLE_COLUMNS) {
                int localX = column[0];
                int localZ = column[1];
                int x = chunkPos.getMinBlockX() + localX;
                int z = chunkPos.getMinBlockZ() + localZ;
                int y = Mth.clamp(chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, localX, localZ) + 1,
                        level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
                BlockPos pos = new BlockPos(x, y, z);
                samples.add(new LocalClimateSample(pos, chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z))));
            }
            return new LocalClimateSamples(samples);
        }

        private int size() {
            return samples.size();
        }
    }

    private record LocalClimateSample(BlockPos pos, Holder<Biome> biome) {
    }

    private static final class ReplayPlanBuilder {
        private final long windowTicks;
        private final String source;
        private final long fallbackDayTime;
        private final long historyStartGameTime;
        private final long historyEndGameTime;
        private final ArrayList<ClimateTickrateChunkStamp.ReplaySegment> replaySegments = new ArrayList<>();
        private final ArrayList<RandomSpan> randomSpans = new ArrayList<>();
        private ReplayPlanBuilder(long windowTicks, String source, long fallbackDayTime, long historyStartGameTime, long historyEndGameTime) {
            this.windowTicks = Math.max(0L, windowTicks);
            this.source = source;
            this.fallbackDayTime = Math.max(0L, fallbackDayTime);
            this.historyStartGameTime = Math.max(0L, Math.min(historyStartGameTime, historyEndGameTime));
            this.historyEndGameTime = Math.max(this.historyStartGameTime, Math.max(0L, historyEndGameTime));
        }

        private void addRandomSpan(long ticks, long dayTime, ClimateSnapshot.WeatherState weatherState) {
            if (ticks <= 0L) return;
            randomSpans.add(new RandomSpan(ticks, weatherState, dayTime));
        }

        private void addAction(ClimateTickrateChunkStamp.ReplayAction action, long ticks, long dayTime, ClimateSnapshot.WeatherState weatherState) {
            if (ticks <= 0L) return;
            ClimateSnapshot.WeatherState replayWeather = action == ClimateTickrateChunkStamp.ReplayAction.SNOW && weatherState == ClimateSnapshot.WeatherState.CLEAR
                        ? ClimateSnapshot.WeatherState.RAIN
                        : weatherState;
            replaySegments.add(new ClimateTickrateChunkStamp.ReplaySegment(action, ticks, replayWeather, dayTime));
        }

        private ReplayPlan finish() {
            long replayWindowTicks = Math.max(0L, ArcticNightsConfig.tickrateCatchupMaxDebtTicks.get());
            List<ClimateTickrateChunkStamp.ReplaySegment> trimmedSegments = trimToLatestReplayDebt(replaySegments, replayWindowTicks);
            long replayTicks = replayTicks(trimmedSegments);
            long randomTickDebt = Math.min(windowTicks, ArcticNightsConfig.tickrateCatchupMaxRandomTickDebtTicks.get());
            randomTickDebt = Math.max(randomTickDebt, replayTicks);
            RandomClimate randomClimate = randomClimate(randomTickDebt);
            return new ReplayPlan(source, randomTickDebt, randomClimate.weatherState(), randomClimate.dayTime(),
                    trimmedSegments, historyStartGameTime, historyEndGameTime);
        }

        private RandomClimate randomClimate(long ticks) {
            long total = randomSpanTicks(randomSpans);
            long keep = Math.max(0L, Math.min(total, ticks));
            if (keep <= 0L) {
                return new RandomClimate(ClimateSnapshot.WeatherState.CLEAR, fallbackDayTime);
            }

            long skip = total - keep;
            long clearTicks = 0L;
            long rainTicks = 0L;
            long thunderTicks = 0L;
            double weightedDayTime = 0.0D;
            long usedTicks = 0L;
            for (RandomSpan span : randomSpans) {
                if (skip >= span.ticks()) {
                    skip -= span.ticks();
                    continue;
                }

                long spanTicks = span.ticks() - skip;
                skip = 0L;
                usedTicks += spanTicks;
                weightedDayTime += span.dayTime() * (double) spanTicks;
                if (span.weatherState() == ClimateSnapshot.WeatherState.THUNDER) {
                    thunderTicks += spanTicks;
                } else if (span.weatherState() == ClimateSnapshot.WeatherState.RAIN) {
                    rainTicks += spanTicks;
                } else {
                    clearTicks += spanTicks;
                }
            }

            ClimateSnapshot.WeatherState weatherState = dominantWeather(clearTicks, rainTicks, thunderTicks);
            long dayTime = usedTicks <= 0L ? fallbackDayTime : Math.max(0L, Math.round(weightedDayTime / usedTicks));
            return new RandomClimate(weatherState, dayTime);
        }
    }

    private static ClimateSnapshot.WeatherState dominantWeather(long clearTicks, long rainTicks, long thunderTicks) {
        if (thunderTicks >= rainTicks && thunderTicks >= clearTicks) return ClimateSnapshot.WeatherState.THUNDER;
        if (rainTicks >= clearTicks) return ClimateSnapshot.WeatherState.RAIN;
        return ClimateSnapshot.WeatherState.CLEAR;
    }

    private static long randomSpanTicks(List<RandomSpan> spans) {
        long ticks = 0L;
        for (RandomSpan span : spans) {
            ticks += span.ticks();
        }
        return ticks;
    }

    private static List<ClimateTickrateChunkStamp.ReplaySegment> trimToLatestReplayDebt(
            List<ClimateTickrateChunkStamp.ReplaySegment> segments, long maxTicks) {
        long total = replayTicks(segments);
        long keep = Math.max(0L, Math.min(total, maxTicks));
        if (keep <= 0L) return List.of();
        long skip = total - keep;
        ArrayList<ClimateTickrateChunkStamp.ReplaySegment> trimmed = new ArrayList<>();
        for (ClimateTickrateChunkStamp.ReplaySegment segment : segments) {
            if (skip >= segment.ticks()) {
                skip -= segment.ticks();
                continue;
            }
            if (skip > 0L) {
                trimmed.add(segment.withTicks(segment.ticks() - skip));
                skip = 0L;
            } else {
                trimmed.add(segment);
            }
        }
        return trimmed;
    }

    private static long replayTicks(List<ClimateTickrateChunkStamp.ReplaySegment> segments) {
        long ticks = 0L;
        for (ClimateTickrateChunkStamp.ReplaySegment segment : segments) {
            ticks += segment.ticks();
        }
        return ticks;
    }

    private static final class RuntimeState {
        private final LinkedHashMap<Long, TickResult> results = new LinkedHashMap<>();
        private long gameTime = Long.MIN_VALUE;
        private int burstChunksThisTick;
        private int wantedChunksThisTick;
        private double wantedWeightThisTick;
        private double smoothedWantedChunks;
        private double smoothedWeightSum;

        private void beginTick(long gameTime) {
            if (this.gameTime == gameTime) return;
            rollBurstPressureForward();
            this.gameTime = gameTime;
            this.burstChunksThisTick = 0;
            this.wantedChunksThisTick = 0;
            this.wantedWeightThisTick = 0.0D;
        }

        private void rollBurstPressureForward() {
            if (this.gameTime == Long.MIN_VALUE) return;
            if (wantedChunksThisTick <= 0) {
                smoothedWantedChunks = lerp(smoothedWantedChunks, 0.0D);
                smoothedWeightSum = lerp(smoothedWeightSum, 0.0D);
                if (smoothedWantedChunks < 1.0D) {
                    smoothedWantedChunks = 0.0D;
                    smoothedWeightSum = 0.0D;
                }
                return;
            }

            if (smoothedWantedChunks <= 0.0D) {
                smoothedWantedChunks = wantedChunksThisTick;
                smoothedWeightSum = wantedWeightThisTick;
                return;
            }

            smoothedWantedChunks = lerp(smoothedWantedChunks, wantedChunksThisTick);
            smoothedWeightSum = lerp(smoothedWeightSum, wantedWeightThisTick);
        }

        private boolean shouldBurstThisTick(ServerLevel level, ChunkPos pos, ClimateTickrateChunkStamp stamp, long gameTime) {
            double baseChance = baseBurstChance();
            double expectedIntervalTicks = 1.0D / Math.max(baseChance, 0.000_001D);
            double weight = burstAgeWeight(stamp, gameTime, expectedIntervalTicks);
            wantedChunksThisTick++;
            wantedWeightThisTick += weight;

            double chance = clamp(baseChance * weight / meanBurstWeight(), 0.0D, 1.0D);
            return deterministicRoll(level, pos, gameTime) < chance;
        }

        private boolean claimBurstChunk() {
            if (burstChunksThisTick >= emergencyBurstChunksPerTick()) {
                return false;
            }
            burstChunksThisTick++;
            return true;
        }

        private double baseBurstChance() {
            if (smoothedWantedChunks <= 0.0D) return 1.0D;
            return clamp(targetBurstChunksPerTick() / smoothedWantedChunks, 0.0D, 1.0D);
        }

        private double meanBurstWeight() {
            if (smoothedWantedChunks <= 0.0D || smoothedWeightSum <= 0.0D) return 1.0D;
            return Math.max(0.000_001D, smoothedWeightSum / smoothedWantedChunks);
        }
    }

    private record Exposure(
            long historyWindowTicks,
            long snowTicks,
            long meltTicks,
            long neutralTicks,
            long actionTicks,
            ClimateTickrateChunkStamp.ReplayAction action,
            ClimateSnapshot.WeatherState replayWeatherState,
            long replayDayTime
    ) {
        private static Exposure none() {
            return new Exposure(0L, 0L, 0L, 0L, 0L,
                    ClimateTickrateChunkStamp.ReplayAction.NONE, ClimateSnapshot.WeatherState.CLEAR, 0L);
        }

        private static Exposure pending(ClimateTickrateChunkStamp stamp, ClimateTickrateChunkStamp.ReplaySegment replaySegment) {
            long snowTicks = stamp.pendingReplayTicks(ClimateTickrateChunkStamp.ReplayAction.SNOW);
            long meltTicks = stamp.pendingReplayTicks(ClimateTickrateChunkStamp.ReplayAction.MELT);
            long actionTicks = snowTicks + meltTicks;
            long windowTicks = Math.max(stamp.pendingRandomTicks(), actionTicks);
            long neutralTicks = Math.max(0L, windowTicks - actionTicks);
            if (replaySegment != null) {
                return new Exposure(windowTicks, snowTicks, meltTicks, neutralTicks, actionTicks,
                        replaySegment.action(), replaySegment.weather(), replaySegment.dayTime());
            }

            return new Exposure(windowTicks, snowTicks, meltTicks, neutralTicks, actionTicks,
                    ClimateTickrateChunkStamp.ReplayAction.NONE,
                    stamp.pendingRandomWeather(),
                    stamp.pendingRandomDayTime());
        }

        private double snowFraction() {
            return historyWindowTicks <= 0L ? 0.0D : Mth.clamp(snowTicks / (double) historyWindowTicks, 0.0D, 1.0D);
        }

        private double meltFraction() {
            return historyWindowTicks <= 0L ? 0.0D : Mth.clamp(meltTicks / (double) historyWindowTicks, 0.0D, 1.0D);
        }

    }

    private record ReplayPlan(
            String source,
            long randomTickDebtTicks,
            ClimateSnapshot.WeatherState randomWeatherState,
            long randomDayTime,
            List<ClimateTickrateChunkStamp.ReplaySegment> replaySegments,
            long historyStartGameTime,
            long historyEndGameTime
    ) {
        private static ReplayPlan zero(String source) {
            return new ReplayPlan(source, 0L, ClimateSnapshot.WeatherState.CLEAR, 0L, List.of(), 0L, 0L);
        }

        private boolean hasWork() {
            return randomTickDebtTicks > 0L || !replaySegments.isEmpty();
        }

        private String source(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isEmpty() ? "" : prefix + "_";
            if (!replaySegments.isEmpty()) {
                return normalizedPrefix + replaySegments.get(0).action().name().toLowerCase(java.util.Locale.ROOT) + "_" + source;
            }
            if (randomTickDebtTicks > 0L) {
                return normalizedPrefix + "random_" + source;
            }
            return normalizedPrefix + source;
        }

        private long replayTicks() {
            return ClimateTickrateCatchup.replayTicks(replaySegments);
        }

        private ClimateTickrateChunkStamp.ReplayAction firstReplayAction() {
            return replaySegments.isEmpty()
                    ? ClimateTickrateChunkStamp.ReplayAction.NONE
                    : replaySegments.get(0).action();
        }

        private long replayTicks(ClimateTickrateChunkStamp.ReplayAction action) {
            if (action == ClimateTickrateChunkStamp.ReplayAction.NONE) return 0L;
            long ticks = 0L;
            for (ClimateTickrateChunkStamp.ReplaySegment segment : replaySegments) {
                if (segment.action() == action) ticks += segment.ticks();
            }
            return ticks;
        }
    }

    private record HistoricalClimateReplay(
            ServerLevel level,
            ClimateTickrateChunkStamp.ReplayAction action,
            ClimateSnapshot.WeatherState weatherState,
            long dayTime
    ) {
    }

    private record RandomSpan(
            long ticks,
            ClimateSnapshot.WeatherState weatherState,
            long dayTime
    ) {
        private RandomSpan {
            ticks = Math.max(0L, ticks);
            weatherState = weatherState == null ? ClimateSnapshot.WeatherState.CLEAR : weatherState;
            dayTime = Math.max(0L, dayTime);
        }
    }

    private record RandomClimate(
            ClimateSnapshot.WeatherState weatherState,
            long dayTime
    ) {
    }

    public record InspectResult(
            ChunkPos pos,
            boolean loaded,
            Optional<ClimateTickrateChunkStamp> stamp,
            long debtTicks,
            int activeBurstTicks,
            Optional<TickResult> lastResult
    ) {
    }

    public record TickResult(
            ChunkPos pos,
            String status,
            long debtTicks,
            int multiplier,
            int baseRandomTickSpeed,
            int effectiveRandomTickSpeed,
            long appliedTicks,
            int burstTicks,
            long historyWindowTicks,
            long snowTicks,
            long meltTicks,
            long neutralTicks,
            double snowFraction,
            double meltFraction,
            long actionTicks,
            ClimateTickrateChunkStamp.ReplayAction replayAction,
            ClimateSnapshot.WeatherState replayWeatherState,
            long replayDayTime,
            String source
    ) {
        private static TickResult normal(ChunkPos pos, long debt, int baseRandomTickSpeed, long appliedTicks, String source) {
            return new TickResult(pos, "normal", debt, 1, baseRandomTickSpeed, baseRandomTickSpeed,
                    appliedTicks, 0, 0L, 0L, 0L, 0L, 0.0D, 0.0D, 0L,
                    ClimateTickrateChunkStamp.ReplayAction.NONE, ClimateSnapshot.WeatherState.CLEAR, 0L, source);
        }

        private static TickResult reset(ChunkPos pos, String status, int baseRandomTickSpeed) {
            return new TickResult(pos, status, 0L, 1, baseRandomTickSpeed, baseRandomTickSpeed,
                    1L, 0, 0L, 0L, 0L, 0L, 0.0D, 0.0D, 0L,
                    ClimateTickrateChunkStamp.ReplayAction.NONE, ClimateSnapshot.WeatherState.CLEAR, 0L, "time_reset");
        }

        private static TickResult skipped(ChunkPos pos, String status, long debt, int baseRandomTickSpeed, long appliedTicks, String source) {
            return new TickResult(pos, status, debt, 1, baseRandomTickSpeed, baseRandomTickSpeed,
                    appliedTicks, 0, 0L, 0L, 0L, 0L, 0.0D, 0.0D, 0L,
                    ClimateTickrateChunkStamp.ReplayAction.NONE, ClimateSnapshot.WeatherState.CLEAR, 0L, source);
        }

        private static TickResult burst(ChunkPos pos, long debt, int multiplier, int baseRandomTickSpeed,
                                        int effectiveRandomTickSpeed, long appliedTicks, int burstTicks, Exposure exposure, String source) {
            return new TickResult(pos, "burst", debt, multiplier, baseRandomTickSpeed, effectiveRandomTickSpeed,
                    appliedTicks, burstTicks, exposure.historyWindowTicks(), exposure.snowTicks(), exposure.meltTicks(),
                    exposure.neutralTicks(), exposure.snowFraction(), exposure.meltFraction(), exposure.actionTicks(),
                    exposure.action(), exposure.replayWeatherState(), exposure.replayDayTime(), source);
        }
    }
}
