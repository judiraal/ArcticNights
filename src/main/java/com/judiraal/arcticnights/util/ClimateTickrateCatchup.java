package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsAttachments;
import com.judiraal.arcticnights.ArcticNightsConfig;
import com.judiraal.arcticnights.ArcticNightsFeatures;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class ClimateTickrateCatchup {
    private static final int RECENT_RESULTS = 128;
    private static final int HISTORY_SAVE_INTERVAL_TICKS = 20;
    private static final Map<ServerLevel, RuntimeState> RUNTIME = new WeakHashMap<>();

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
            RuntimeState runtime = runtime(level);
            if (history.isDirty() && runtime.shouldSaveHistory(level.getGameTime())) {
                level.getDataStorage().save();
            }
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
        TickPlan plan = plan(level, chunk, Math.max(0, baseRandomTickSpeed), runtime);
        level.tickChunk(chunk, plan.result().effectiveRandomTickSpeed());
        MachineCatchupBridge.tickSupportedBlockEntities(level, chunk, plan.result());
        applyPlan(chunk, plan);
        TickResult result = plan.result();
        if (!"normal".equals(result.status())) {
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

    private static TickPlan plan(ServerLevel level, LevelChunk chunk, int baseRandomTickSpeed, RuntimeState runtime) {
        ChunkPos pos = chunk.getPos();
        long gameTime = level.getGameTime();
        long dayTime = level.getDayTime();
        int threshold = ArcticNightsConfig.tickrateCatchupDebtThresholdTicks.get();
        ClimateTickrateChunkStamp stamp = stamp(chunk).orElse(null);
        boolean newStamp = stamp == null;
        DebtSeed debtSeed = DebtSeed.existing();
        if (newStamp) {
            debtSeed = newChunkDebtSeed(level, gameTime, dayTime);
            long lastApplied = Math.max(0L, gameTime - debtSeed.debtTicks());
            stamp = new ClimateTickrateChunkStamp(lastApplied, gameTime, 0);
        }

        long lastApplied = stamp.lastAppliedGameTime();
        if (gameTime < lastApplied) {
            TickResult result = TickResult.reset(pos, "time_rewind", baseRandomTickSpeed);
            return new TickPlan(stamp, gameTime, gameTime, 0, true, result);
        }

        long debt = Math.min(gameTime - lastApplied, ArcticNightsConfig.tickrateCatchupMaxDebtTicks.get());
        if (debt <= threshold) {
            boolean shouldPersist = newStamp || debt >= Math.max(1, threshold);
            long appliedTicks = baseRandomTickSpeed > 0 ? 1L : 0L;
            TickResult result = TickResult.normal(pos, debt, baseRandomTickSpeed, appliedTicks, debtSeed.source());
            long markedApplied = shouldPersist ? gameTime : stamp.lastAppliedGameTime();
            long markedSeen = shouldPersist ? gameTime : stamp.lastSeenGameTime();
            int markedBurstTicks = shouldPersist ? 0 : stamp.burstTicks();
            return new TickPlan(stamp, markedApplied, markedSeen, markedBurstTicks, shouldPersist, result);
        }

        if (stamp.burstTicks() >= ArcticNightsConfig.tickrateCatchupMaxBurstTicks.get()) {
            TickResult result = TickResult.skipped(pos, "max_duration", debt, baseRandomTickSpeed, 0L, debtSeed.source());
            return new TickPlan(stamp, gameTime, gameTime, 0, true, result);
        }

        if (baseRandomTickSpeed <= 0) {
            TickResult result = TickResult.skipped(pos, "random_tick_disabled", debt, baseRandomTickSpeed, 0L, debtSeed.source());
            return new TickPlan(stamp, lastApplied, gameTime, stamp.burstTicks(), true, result);
        }

        if (!runtime.claimBurstChunk()) {
            long newLastApplied = Math.min(gameTime, lastApplied + 1L);
            TickResult result = TickResult.skipped(pos, "budget", debt, baseRandomTickSpeed, 1L, debtSeed.source());
            return new TickPlan(stamp, newLastApplied, gameTime, stamp.burstTicks(), true, result);
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
        Exposure exposure = debtSeed.exposure().orElseGet(() -> exposure(ClimateHistoryState.get(level), dayTime, debt));
        TickResult result = TickResult.burst(pos, debt, multiplier, baseRandomTickSpeed, effectiveRandomTickSpeed,
                appliedTicks, burstTicks, exposure, debtSeed.source());
        return new TickPlan(stamp, newLastApplied, gameTime, burstTicks, true, result);
    }

    private static void applyPlan(LevelChunk chunk, TickPlan plan) {
        if (!plan.persist()) return;
        plan.stamp().mark(plan.lastAppliedGameTime(), plan.lastSeenGameTime(), plan.burstTicks());
        chunk.setData(ArcticNightsAttachments.TICKRATE_CATCHUP.get(), plan.stamp());
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

    private static DebtSeed newChunkDebtSeed(ServerLevel level, long gameTime, long dayTime) {
        if (!ArcticNightsConfig.tickrateCatchupNewChunksUseRecentExposure.get()) {
            return DebtSeed.zero("new_disabled");
        }

        long maxWindow = Math.min(gameTime, Math.min(ArcticNightsConfig.tickrateCatchupMaxDebtTicks.get(),
                ArcticNightsConfig.tickrateCatchupHistoryWindowTicks.get()));
        long window = Math.min(maxWindow, Math.max(0L, dayTime));
        if (window <= 0L) {
            return DebtSeed.zero("new_no_history");
        }

        long fromDayTime = Math.max(0L, dayTime - window);
        long precipitationTicks = ClimateHistoryState.get(level).precipitationTicksBetween(fromDayTime, dayTime);
        long debt = Math.min(precipitationTicks, ArcticNightsConfig.tickrateCatchupMaxDebtTicks.get());
        double fraction = Mth.clamp(precipitationTicks / (double) window, 0.0D, 1.0D);
        return new DebtSeed("new_precip_history", debt, Optional.of(new Exposure(window, precipitationTicks, fraction)));
    }

    private static Exposure exposure(ClimateHistoryState history, long dayTime, long debt) {
        long window = Math.min(Math.min(debt, ArcticNightsConfig.tickrateCatchupHistoryWindowTicks.get()), Math.max(1L, dayTime));
        long fromDayTime = Math.max(0L, dayTime - window);
        long precipitationTicks = history.precipitationTicksBetween(fromDayTime, dayTime);
        double fraction = window <= 0L ? 0.0D : Mth.clamp(precipitationTicks / (double) window, 0.0D, 1.0D);
        return new Exposure(window, precipitationTicks, fraction);
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
        ArcticNights.LOGGER.debug("Climate tick-rate catch-up chunk {},{} status={} source={} debt={} multiplier={} baseRandomTickSpeed={} effectiveRandomTickSpeed={} appliedTicks={} burstTicks={} window={} precipTicks={} precipFraction={} activeBursts={} recent={}",
                result.pos().x, result.pos().z, result.status(), result.source(), result.debtTicks(), result.multiplier(),
                result.baseRandomTickSpeed(), result.effectiveRandomTickSpeed(), result.appliedTicks(), result.burstTicks(),
                result.historyWindowTicks(), result.precipitationTicks(), String.format(java.util.Locale.ROOT, "%.3f", result.precipitationFraction()),
                runtime.burstChunksThisTick, runtime.results.size());
    }

    private static RuntimeState runtime(ServerLevel level) {
        return RUNTIME.computeIfAbsent(level, ignored -> new RuntimeState());
    }

    private static boolean shouldTrack(ServerLevel level) {
        return ArcticNightsFeatures.tickrateClimateCatchup() && level.dimension() == Level.OVERWORLD && ArcticNights.appliesTo(level);
    }

    private static final class RuntimeState {
        private final LinkedHashMap<Long, TickResult> results = new LinkedHashMap<>();
        private long gameTime = Long.MIN_VALUE;
        private long lastHistorySaveGameTime = Long.MIN_VALUE;
        private int burstChunksThisTick;

        private void beginTick(long gameTime) {
            if (this.gameTime == gameTime) return;
            this.gameTime = gameTime;
            this.burstChunksThisTick = 0;
        }

        private boolean claimBurstChunk() {
            if (burstChunksThisTick >= ArcticNightsConfig.tickrateCatchupMaxBurstChunksPerTick.get()) {
                return false;
            }
            burstChunksThisTick++;
            return true;
        }

        private boolean shouldSaveHistory(long gameTime) {
            if (lastHistorySaveGameTime == Long.MIN_VALUE || gameTime - lastHistorySaveGameTime >= HISTORY_SAVE_INTERVAL_TICKS) {
                lastHistorySaveGameTime = gameTime;
                return true;
            }
            return false;
        }
    }

    private record TickPlan(
            ClimateTickrateChunkStamp stamp,
            long lastAppliedGameTime,
            long lastSeenGameTime,
            int burstTicks,
            boolean persist,
            TickResult result
    ) {
    }

    private record Exposure(long historyWindowTicks, long precipitationTicks, double precipitationFraction) {
    }

    private record DebtSeed(String source, long debtTicks, Optional<Exposure> exposure) {
        private static DebtSeed existing() {
            return new DebtSeed("stamp_elapsed", 0L, Optional.empty());
        }

        private static DebtSeed zero(String source) {
            return new DebtSeed(source, 0L, Optional.of(new Exposure(0L, 0L, 0.0D)));
        }
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
            long precipitationTicks,
            double precipitationFraction,
            String source
    ) {
        private static TickResult normal(ChunkPos pos, long debt, int baseRandomTickSpeed, long appliedTicks, String source) {
            return new TickResult(pos, "normal", debt, 1, baseRandomTickSpeed, baseRandomTickSpeed,
                    appliedTicks, 0, 0L, 0L, 0.0D, source);
        }

        private static TickResult reset(ChunkPos pos, String status, int baseRandomTickSpeed) {
            return new TickResult(pos, status, 0L, 1, baseRandomTickSpeed, baseRandomTickSpeed,
                    1L, 0, 0L, 0L, 0.0D, "time_reset");
        }

        private static TickResult skipped(ChunkPos pos, String status, long debt, int baseRandomTickSpeed, long appliedTicks, String source) {
            return new TickResult(pos, status, debt, 1, baseRandomTickSpeed, baseRandomTickSpeed,
                    appliedTicks, 0, 0L, 0L, 0.0D, source);
        }

        private static TickResult burst(ChunkPos pos, long debt, int multiplier, int baseRandomTickSpeed,
                                        int effectiveRandomTickSpeed, long appliedTicks, int burstTicks, Exposure exposure, String source) {
            return new TickResult(pos, "burst", debt, multiplier, baseRandomTickSpeed, effectiveRandomTickSpeed,
                    appliedTicks, burstTicks, exposure.historyWindowTicks(), exposure.precipitationTicks(), exposure.precipitationFraction(), source);
        }
    }
}
