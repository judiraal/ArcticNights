package com.judiraal.arcticnights.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.ArrayDeque;
import java.util.List;

public final class ClimateTickrateChunkStamp implements INBTSerializable<CompoundTag> {
    private long lastAppliedGameTime;
    private long lastSeenGameTime;
    private long lastBurstGameTime;
    private int burstTicks;
    private long pendingRandomTicks;
    private ClimateSnapshot.WeatherState pendingRandomWeather = ClimateSnapshot.WeatherState.CLEAR;
    private long pendingRandomDayTime;
    private final ArrayDeque<ReplaySegment> pendingReplaySegments = new ArrayDeque<>();

    public ClimateTickrateChunkStamp() {
    }

    public ClimateTickrateChunkStamp(long lastAppliedGameTime, long lastSeenGameTime, int burstTicks) {
        this(lastAppliedGameTime, lastSeenGameTime, 0L, burstTicks);
    }

    public ClimateTickrateChunkStamp(long lastAppliedGameTime, long lastSeenGameTime, long lastBurstGameTime, int burstTicks) {
        this.lastAppliedGameTime = Math.max(0L, lastAppliedGameTime);
        this.lastSeenGameTime = Math.max(0L, lastSeenGameTime);
        this.lastBurstGameTime = Math.max(0L, Math.min(lastBurstGameTime, this.lastSeenGameTime));
        this.burstTicks = Math.max(0, burstTicks);
    }

    public long lastAppliedGameTime() {
        return lastAppliedGameTime;
    }

    public long lastSeenGameTime() {
        return lastSeenGameTime;
    }

    public long lastBurstGameTime() {
        return lastBurstGameTime;
    }

    public int burstTicks() {
        return burstTicks;
    }

    public long pendingRandomTicks() {
        return pendingRandomTicks;
    }

    public ClimateSnapshot.WeatherState pendingRandomWeather() {
        return pendingRandomWeather;
    }

    public long pendingRandomDayTime() {
        return pendingRandomDayTime;
    }

    public ReplayAction pendingReplayAction() {
        ReplaySegment segment = pendingReplaySegment();
        return segment == null ? ReplayAction.NONE : segment.action();
    }

    public ClimateSnapshot.WeatherState pendingReplayWeather() {
        ReplaySegment segment = pendingReplaySegment();
        return segment == null ? ClimateSnapshot.WeatherState.CLEAR : segment.weather();
    }

    public long pendingReplayDayTime() {
        ReplaySegment segment = pendingReplaySegment();
        return segment == null ? 0L : segment.dayTime();
    }

    public long pendingReplayTicks() {
        long ticks = 0L;
        for (ReplaySegment segment : pendingReplaySegments) {
            ticks += segment.ticks();
        }
        return ticks;
    }

    public long pendingReplayTicks(ReplayAction action) {
        if (action == ReplayAction.NONE) return 0L;
        long ticks = 0L;
        for (ReplaySegment segment : pendingReplaySegments) {
            if (segment.action() == action) ticks += segment.ticks();
        }
        return ticks;
    }

    public int pendingReplaySegments() {
        return pendingReplaySegments.size();
    }

    public ReplaySegment pendingReplaySegment() {
        return pendingReplaySegments.peekFirst();
    }

    public boolean hasPendingReplay() {
        return pendingReplaySegment() != null;
    }

    public boolean hasPendingWork() {
        return pendingRandomTicks > 0L || hasPendingReplay();
    }

    public void setPendingReplay(ReplayAction action, long ticks, ClimateSnapshot.WeatherState weather, long dayTime) {
        if (action == ReplayAction.NONE || ticks <= 0L) {
            clearPendingReplay();
            return;
        }

        pendingReplaySegments.clear();
        pendingReplaySegments.addLast(new ReplaySegment(action, Math.max(0L, ticks), weather, Math.max(0L, dayTime)));
    }

    public void setPendingPlan(long randomTicks, ClimateSnapshot.WeatherState randomWeather, long randomDayTime, List<ReplaySegment> replaySegments) {
        pendingRandomTicks = Math.max(0L, randomTicks);
        pendingRandomWeather = randomWeather == null ? ClimateSnapshot.WeatherState.CLEAR : randomWeather;
        pendingRandomDayTime = Math.max(0L, randomDayTime);
        pendingReplaySegments.clear();
        for (ReplaySegment segment : replaySegments) {
            if (segment.action() != ReplayAction.NONE && segment.ticks() > 0L) {
                pendingReplaySegments.addLast(segment.normalized());
            }
        }
    }

    public void consumePendingWork(long ticks) {
        long remaining = Math.max(0L, ticks);
        if (remaining <= 0L) return;

        pendingRandomTicks = Math.max(0L, pendingRandomTicks - remaining);
        while (remaining > 0L && !pendingReplaySegments.isEmpty()) {
            ReplaySegment segment = pendingReplaySegments.removeFirst();
            if (remaining < segment.ticks()) {
                pendingReplaySegments.addFirst(segment.withTicks(segment.ticks() - remaining));
                break;
            }
            remaining -= segment.ticks();
        }
    }

    public void clearPendingReplay() {
        pendingReplaySegments.clear();
    }

    public void clearPendingWork() {
        pendingRandomTicks = 0L;
        pendingRandomWeather = ClimateSnapshot.WeatherState.CLEAR;
        pendingRandomDayTime = 0L;
        clearPendingReplay();
    }

    public void mark(long lastAppliedGameTime, long lastSeenGameTime, int burstTicks) {
        this.lastAppliedGameTime = Math.max(0L, lastAppliedGameTime);
        this.lastSeenGameTime = Math.max(0L, lastSeenGameTime);
        this.lastBurstGameTime = Math.min(this.lastBurstGameTime, this.lastSeenGameTime);
        this.burstTicks = Math.max(0, burstTicks);
    }

    public void markBurst(long lastBurstGameTime) {
        this.lastBurstGameTime = Math.max(0L, Math.min(lastBurstGameTime, lastSeenGameTime));
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("lastAppliedGameTime", lastAppliedGameTime);
        tag.putLong("lastSeenGameTime", lastSeenGameTime);
        tag.putLong("lastBurstGameTime", lastBurstGameTime);
        tag.putInt("burstTicks", burstTicks);
        tag.putLong("pendingRandomTicks", pendingRandomTicks);
        if (pendingRandomTicks > 0L) {
            tag.putString("pendingRandomWeather", pendingRandomWeather.name());
            tag.putLong("pendingRandomDayTime", pendingRandomDayTime);
        }
        if (hasPendingReplay()) {
            ListTag segments = new ListTag();
            for (ReplaySegment segment : pendingReplaySegments) {
                CompoundTag segmentTag = new CompoundTag();
                segmentTag.putString("action", segment.action().name());
                segmentTag.putString("weather", segment.weather().name());
                segmentTag.putLong("dayTime", segment.dayTime());
                segmentTag.putLong("ticks", segment.ticks());
                segments.add(segmentTag);
            }
            tag.put("pendingReplaySegments", segments);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        lastAppliedGameTime = tag.getLong("lastAppliedGameTime");
        lastSeenGameTime = tag.getLong("lastSeenGameTime");
        lastBurstGameTime = Math.max(0L, Math.min(tag.getLong("lastBurstGameTime"), lastSeenGameTime));
        burstTicks = tag.getInt("burstTicks");
        pendingRandomTicks = Math.max(0L, tag.getLong("pendingRandomTicks"));
        pendingRandomWeather = weatherState(tag.getString("pendingRandomWeather"));
        pendingRandomDayTime = Math.max(0L, tag.getLong("pendingRandomDayTime"));
        if (pendingRandomTicks <= 0L) {
            pendingRandomWeather = ClimateSnapshot.WeatherState.CLEAR;
            pendingRandomDayTime = 0L;
        }
        pendingReplaySegments.clear();

        if (tag.contains("pendingReplaySegments", Tag.TAG_LIST)) {
            ListTag segments = tag.getList("pendingReplaySegments", Tag.TAG_COMPOUND);
            for (int i = 0; i < segments.size(); i++) {
                CompoundTag segmentTag = segments.getCompound(i);
                ReplayAction action = replayAction(segmentTag.getString("action"));
                long ticks = Math.max(0L, segmentTag.getLong("ticks"));
                if (action == ReplayAction.NONE || ticks <= 0L) continue;
                pendingReplaySegments.addLast(new ReplaySegment(action, ticks,
                        weatherState(segmentTag.getString("weather")),
                        Math.max(0L, segmentTag.getLong("dayTime"))));
            }
        } else {
            ReplayAction action = replayAction(tag.getString("pendingReplayAction"));
            long ticks = Math.max(0L, tag.getLong("pendingReplayTicks"));
            if (action != ReplayAction.NONE && ticks > 0L) {
                pendingReplaySegments.addLast(new ReplaySegment(action, ticks,
                        weatherState(tag.getString("pendingReplayWeather")),
                        Math.max(0L, tag.getLong("pendingReplayDayTime"))));
            }
        }
    }

    private static ReplayAction replayAction(String name) {
        try {
            return ReplayAction.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return ReplayAction.NONE;
        }
    }

    private static ClimateSnapshot.WeatherState weatherState(String name) {
        try {
            return ClimateSnapshot.WeatherState.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return ClimateSnapshot.WeatherState.CLEAR;
        }
    }

    public enum ReplayAction {
        NONE,
        SNOW,
        MELT
    }

    public record ReplaySegment(ReplayAction action, long ticks, ClimateSnapshot.WeatherState weather, long dayTime) {
        public ReplaySegment {
            action = action == null ? ReplayAction.NONE : action;
            ticks = Math.max(0L, ticks);
            weather = weather == null ? ClimateSnapshot.WeatherState.CLEAR : weather;
            dayTime = Math.max(0L, dayTime);
        }

        private ReplaySegment normalized() {
            return new ReplaySegment(action, ticks, weather, dayTime);
        }

        public ReplaySegment withTicks(long ticks) {
            return new ReplaySegment(action, ticks, weather, dayTime);
        }
    }
}
