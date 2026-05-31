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

import java.util.LinkedHashMap;
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
        long debt = stamp.map(value -> Math.max(0L, currentGameTime - value.lastAppliedGameTime())).orElse(0L);
        int activeBurstTicks = stamp.map(ClimateTickrateChunkStamp::burstTicks).orElse(0);
        return new InspectResult(pos, chunk.isPresent(), stamp, debt, activeBurstTicks, Optional.ofNullable(runtime.results.get(pos.toLong())));
    }

    public static String historyDebug(ServerLevel level, long windowTicks) {
        long dayTime = level.getDayTime();
        long window = Math.max(1L, windowTicks);
        long fromDayTime = Math.max(0L, dayTime - window);
        return ClimateHistoryState.get(level).debugWindow(fromDayTime, dayTime);
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
        DebtSeed debtSeed = null;
        String source = SOURCE_STAMP_ELAPSED;
        if (newStamp) {
            debtSeed = newChunkDebtSeed(level, chunk, gameTime, dayTime);
            long lastApplied = Math.max(0L, gameTime - debtSeed.debtTicks());
            stamp = new ClimateTickrateChunkStamp(lastApplied, gameTime, 0);
            stamp.setPendingReplay(debtSeed.exposure().action(), debtSeed.debtTicks(),
                    debtSeed.exposure().replayWeatherState(), debtSeed.exposure().replayDayTime());
            source = debtSeed.source();
        } else {
            stamp = chunk.getData(attachmentType);
            if (stamp.hasPendingReplay()) {
                source = "pending_" + stamp.pendingReplayAction().name().toLowerCase(java.util.Locale.ROOT);
            }
        }

        long lastApplied = stamp.lastAppliedGameTime();
        if (gameTime < lastApplied) {
            TickResult result = TickResult.reset(pos, "time_rewind", baseRandomTickSpeed);
            level.tickChunk(chunk, baseRandomTickSpeed);
            stamp.clearPendingReplay();
            applyPlan(chunk, stamp, gameTime, gameTime, 0);
            return result;
        }

        long elapsed = gameTime - lastApplied;
        if (!stamp.hasPendingReplay() && elapsed > threshold) {
            DebtSeed elapsedDebtSeed = debtSeed != null
                    ? debtSeed
                    : elapsedDebtSeed(level, chunk, ClimateHistoryState.get(level), dayTime, elapsed);
            if (elapsedDebtSeed.debtTicks() > 0L) {
                stamp.setPendingReplay(elapsedDebtSeed.exposure().action(), elapsedDebtSeed.debtTicks(),
                        elapsedDebtSeed.exposure().replayWeatherState(), elapsedDebtSeed.exposure().replayDayTime());
                source = elapsedDebtSeed.source();
            } else if (debtSeed == null) {
                source = elapsedDebtSeed.source();
            }
        }

        long debt = stamp.hasPendingReplay() ? stamp.pendingReplayTicks() : 0L;
        if (debt <= threshold) {
            boolean shouldPersist = newStamp || debt >= Math.max(1, threshold);
            level.tickChunk(chunk, baseRandomTickSpeed);
            if (stamp.hasPendingReplay()) {
                stamp.clearPendingReplay();
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
            stamp.clearPendingReplay();
            applyPlan(chunk, stamp, gameTime, gameTime, 0);
            return result;
        }

        if (baseRandomTickSpeed <= 0) {
            TickResult result = TickResult.skipped(pos, "random_tick_disabled", debt, baseRandomTickSpeed, 0L, source);
            level.tickChunk(chunk, baseRandomTickSpeed);
            applyPlan(chunk, stamp, lastApplied, gameTime, stamp.burstTicks());
            return result;
        }

        if (!runtime.shouldBurstThisTick(level, pos, stamp, gameTime)) {
            long appliedTicks = stamp.hasPendingReplay() ? 0L : 1L;
            long newLastApplied = Math.min(gameTime, lastApplied + appliedTicks);
            TickResult result = TickResult.skipped(pos, "fairness", debt, baseRandomTickSpeed, appliedTicks, source);
            level.tickChunk(chunk, baseRandomTickSpeed);
            applyPlan(chunk, stamp, newLastApplied, gameTime, stamp.burstTicks());
            return result;
        }

        if (!runtime.claimBurstChunk()) {
            long appliedTicks = stamp.hasPendingReplay() ? 0L : 1L;
            long newLastApplied = Math.min(gameTime, lastApplied + appliedTicks);
            TickResult result = TickResult.skipped(pos, "budget", debt, baseRandomTickSpeed, appliedTicks, source);
            level.tickChunk(chunk, baseRandomTickSpeed);
            applyPlan(chunk, stamp, newLastApplied, gameTime, stamp.burstTicks());
            return result;
        }

        int multiplier = multiplier(debt);
        int effectiveRandomTickSpeed = effectiveRandomTickSpeed(baseRandomTickSpeed, multiplier);
        long appliedTicks = multiplier;
        long newLastApplied = Math.min(gameTime, lastApplied + Math.min(debt, appliedTicks));
        int burstTicks = stamp.burstTicks() + 1;
        if (gameTime - newLastApplied <= threshold) {
            newLastApplied = gameTime;
            burstTicks = 0;
        }
        Exposure exposure = stamp.hasPendingReplay()
                ? pendingExposure(stamp)
                : debtSeed != null && debtSeed.exposure() != null
                ? debtSeed.exposure()
                : Exposure.none();
        TickResult result = TickResult.burst(pos, debt, multiplier, baseRandomTickSpeed, effectiveRandomTickSpeed,
                appliedTicks, burstTicks, exposure, source);
        runWithHistoricalClimateReplay(level, result, () -> level.tickChunk(chunk, effectiveRandomTickSpeed));
        MachineCatchupBridge.tickSupportedBlockEntities(level, chunk, result);
        stamp.consumePendingReplay(appliedTicks);
        if (!stamp.hasPendingReplay()) {
            newLastApplied = gameTime;
            burstTicks = 0;
        }
        applyPlan(chunk, stamp, newLastApplied, gameTime, burstTicks, gameTime);
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
        if (action == ClimateTickrateChunkStamp.ReplayAction.NONE || result.actionTicks() <= 0L) return null;
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

    private static DebtSeed newChunkDebtSeed(ServerLevel level, LevelChunk chunk, long gameTime, long dayTime) {
        if (!ArcticNightsConfig.tickrateCatchupNewChunksUseRecentExposure.get()) {
            return DebtSeed.zero("new_disabled");
        }

        long maxWindow = Math.min(gameTime, ArcticNightsConfig.tickrateCatchupHistoryWindowTicks.get());
        long window = Math.min(maxWindow, Math.max(0L, dayTime));
        if (window <= 0L) {
            return DebtSeed.zero("new_no_history");
        }

        long fromDayTime = Math.max(0L, dayTime - window);
        Exposure exposure = exposureBetween(level, chunk, ClimateHistoryState.get(level), fromDayTime, dayTime);
        long debt = exposure.action() == ClimateTickrateChunkStamp.ReplayAction.SNOW
                ? Math.min(exposure.actionTicks(), ArcticNightsConfig.tickrateCatchupMaxDebtTicks.get())
                : 0L;
        return new DebtSeed(exposure.action() == ClimateTickrateChunkStamp.ReplayAction.SNOW
                ? "new_snow_history"
                : "new_no_snow_history", debt, exposure.limitActionTicks(debt));
    }

    private static DebtSeed elapsedDebtSeed(ServerLevel level, LevelChunk chunk, ClimateHistoryState history, long dayTime, long elapsed) {
        long window = Math.min(Math.min(elapsed, ArcticNightsConfig.tickrateCatchupHistoryWindowTicks.get()), Math.max(1L, dayTime));
        long fromDayTime = Math.max(0L, dayTime - window);
        Exposure exposure = exposureBetween(level, chunk, history, fromDayTime, dayTime);
        long debt = Math.min(exposure.actionTicks(), ArcticNightsConfig.tickrateCatchupMaxDebtTicks.get());
        String source = switch (exposure.action()) {
            case SNOW -> "snow_history";
            case MELT -> "melt_history";
            case NONE -> "no_action_history";
        };
        return new DebtSeed(source, debt, exposure.limitActionTicks(debt));
    }

    private static Exposure exposureBetween(ServerLevel level, LevelChunk chunk, ClimateHistoryState history, long fromDayTime, long toDayTime) {
        long from = Math.min(fromDayTime, toDayTime);
        long to = Math.max(fromDayTime, toDayTime);
        long window = Math.max(0L, to - from);
        if (window <= 0L) return Exposure.none();

        LocalClimateSample sample = LocalClimateSample.of(level, chunk);
        ExposureAccumulator accumulator = new ExposureAccumulator(window);
        history.visitSpansBetween(from, to, (start, end, weatherState) -> {
            long duration = Math.max(0L, end - start);
            if (duration <= 0L) return;

            long sampleDayTime = start + duration / 2L;
            ClimateSnapshot snapshot = ClimateService.sampleSnapshot(level, sample.biome(), sample.pos(), true, weatherState, sampleDayTime);
            double snowFactor = weatherState == ClimateSnapshot.WeatherState.CLEAR
                    ? 0.0D
                    : ClimateService.snowAccumulationFactor(snapshot.outdoorMinecraftTemperature());
            double meltFactor = 1.0D - ClimateService.snowAccumulationFactor(snapshot.outdoorMinecraftTemperature());

            if (snapshot.precipitationKind() == ClimateSnapshot.PrecipitationKind.NONE) {
                snowFactor = 0.0D;
                if (weatherState != ClimateSnapshot.WeatherState.CLEAR) meltFactor = 0.0D;
            }

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

            accumulator.addSnow(snowTicks, sampleDayTime, weatherState);
            accumulator.addMelt(meltTicks, sampleDayTime, weatherState);
            accumulator.addNeutral(Math.max(0L, duration - snowTicks - meltTicks));
        });
        return accumulator.finish();
    }

    private static Exposure pendingExposure(ClimateTickrateChunkStamp stamp) {
        long ticks = stamp.pendingReplayTicks();
        long window = Math.max(0L, ticks);
        return new Exposure(window,
                stamp.pendingReplayAction() == ClimateTickrateChunkStamp.ReplayAction.SNOW ? ticks : 0L,
                stamp.pendingReplayAction() == ClimateTickrateChunkStamp.ReplayAction.MELT ? ticks : 0L,
                0L,
                ticks,
                stamp.pendingReplayAction(),
                stamp.pendingReplayWeather(),
                stamp.pendingReplayDayTime());
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

    private record LocalClimateSample(BlockPos pos, Holder<Biome> biome) {
        private static LocalClimateSample of(ServerLevel level, LevelChunk chunk) {
            ChunkPos chunkPos = chunk.getPos();
            int localX = 8;
            int localZ = 8;
            int x = chunkPos.getMinBlockX() + localX;
            int z = chunkPos.getMinBlockZ() + localZ;
            int y = Mth.clamp(chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, localX, localZ) + 1,
                    level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
            BlockPos pos = new BlockPos(x, y, z);
            return new LocalClimateSample(pos, chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z)));
        }
    }

    private static final class ExposureAccumulator {
        private final long windowTicks;
        private long snowTicks;
        private long meltTicks;
        private long neutralTicks;
        private long snowWeightedDayTime;
        private long meltWeightedDayTime;
        private long snowWeatherTicks;
        private long meltWeatherTicks;
        private ClimateSnapshot.WeatherState snowWeatherState = ClimateSnapshot.WeatherState.RAIN;
        private ClimateSnapshot.WeatherState meltWeatherState = ClimateSnapshot.WeatherState.CLEAR;

        private ExposureAccumulator(long windowTicks) {
            this.windowTicks = Math.max(0L, windowTicks);
        }

        private void addSnow(long ticks, long dayTime, ClimateSnapshot.WeatherState weatherState) {
            if (ticks <= 0L) return;
            snowTicks += ticks;
            snowWeightedDayTime += ticks * Math.max(0L, dayTime);
            if (ticks > snowWeatherTicks) {
                snowWeatherTicks = ticks;
                snowWeatherState = weatherState == ClimateSnapshot.WeatherState.CLEAR
                        ? ClimateSnapshot.WeatherState.RAIN
                        : weatherState;
            }
        }

        private void addMelt(long ticks, long dayTime, ClimateSnapshot.WeatherState weatherState) {
            if (ticks <= 0L) return;
            meltTicks += ticks;
            meltWeightedDayTime += ticks * Math.max(0L, dayTime);
            if (ticks > meltWeatherTicks) {
                meltWeatherTicks = ticks;
                meltWeatherState = weatherState;
            }
        }

        private void addNeutral(long ticks) {
            neutralTicks += Math.max(0L, ticks);
        }

        private Exposure finish() {
            long effectiveNeutralTicks = neutralTicks + Math.max(0L, windowTicks - snowTicks - meltTicks - neutralTicks);
            long actionTicks = Math.abs(snowTicks - meltTicks);
            ClimateTickrateChunkStamp.ReplayAction action = ClimateTickrateChunkStamp.ReplayAction.NONE;
            ClimateSnapshot.WeatherState weatherState = ClimateSnapshot.WeatherState.CLEAR;
            long replayDayTime = 0L;

            if (snowTicks > meltTicks) {
                action = ClimateTickrateChunkStamp.ReplayAction.SNOW;
                weatherState = snowWeatherState;
                replayDayTime = snowTicks == 0L ? 0L : snowWeightedDayTime / snowTicks;
            } else if (meltTicks > snowTicks) {
                action = ClimateTickrateChunkStamp.ReplayAction.MELT;
                weatherState = meltWeatherState;
                replayDayTime = meltTicks == 0L ? 0L : meltWeightedDayTime / meltTicks;
            }

            return new Exposure(windowTicks, snowTicks, meltTicks, effectiveNeutralTicks, actionTicks, action, weatherState, replayDayTime);
        }
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

        private double snowFraction() {
            return historyWindowTicks <= 0L ? 0.0D : Mth.clamp(snowTicks / (double) historyWindowTicks, 0.0D, 1.0D);
        }

        private double meltFraction() {
            return historyWindowTicks <= 0L ? 0.0D : Mth.clamp(meltTicks / (double) historyWindowTicks, 0.0D, 1.0D);
        }

        private Exposure limitActionTicks(long ticks) {
            long limited = Math.max(0L, ticks);
            if (action == ClimateTickrateChunkStamp.ReplayAction.SNOW) {
                return new Exposure(historyWindowTicks, Math.min(snowTicks, limited), meltTicks, neutralTicks,
                        limited, action, replayWeatherState, replayDayTime);
            }
            if (action == ClimateTickrateChunkStamp.ReplayAction.MELT) {
                return new Exposure(historyWindowTicks, snowTicks, Math.min(meltTicks, limited), neutralTicks,
                        limited, action, replayWeatherState, replayDayTime);
            }
            return this;
        }
    }

    private record DebtSeed(String source, long debtTicks, Exposure exposure) {
        private static DebtSeed zero(String source) {
            return new DebtSeed(source, 0L, Exposure.none());
        }
    }

    private record HistoricalClimateReplay(
            ServerLevel level,
            ClimateTickrateChunkStamp.ReplayAction action,
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
