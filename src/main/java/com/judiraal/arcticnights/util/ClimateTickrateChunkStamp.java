package com.judiraal.arcticnights.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class ClimateTickrateChunkStamp implements INBTSerializable<CompoundTag> {
    private long lastAppliedGameTime;
    private long lastSeenGameTime;
    private long lastBurstGameTime;
    private int burstTicks;
    private ReplayAction pendingReplayAction = ReplayAction.NONE;
    private ClimateSnapshot.WeatherState pendingReplayWeather = ClimateSnapshot.WeatherState.CLEAR;
    private long pendingReplayDayTime;
    private long pendingReplayTicks;

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

    public ReplayAction pendingReplayAction() {
        return pendingReplayAction;
    }

    public ClimateSnapshot.WeatherState pendingReplayWeather() {
        return pendingReplayWeather;
    }

    public long pendingReplayDayTime() {
        return pendingReplayDayTime;
    }

    public long pendingReplayTicks() {
        return pendingReplayTicks;
    }

    public boolean hasPendingReplay() {
        return pendingReplayAction != ReplayAction.NONE && pendingReplayTicks > 0L;
    }

    public void setPendingReplay(ReplayAction action, long ticks, ClimateSnapshot.WeatherState weather, long dayTime) {
        if (action == ReplayAction.NONE || ticks <= 0L) {
            clearPendingReplay();
            return;
        }

        pendingReplayAction = action;
        pendingReplayWeather = weather;
        pendingReplayDayTime = Math.max(0L, dayTime);
        pendingReplayTicks = Math.max(0L, ticks);
    }

    public void consumePendingReplay(long ticks) {
        if (!hasPendingReplay()) return;
        pendingReplayTicks = Math.max(0L, pendingReplayTicks - Math.max(0L, ticks));
        if (pendingReplayTicks <= 0L) {
            clearPendingReplay();
        }
    }

    public void clearPendingReplay() {
        pendingReplayAction = ReplayAction.NONE;
        pendingReplayWeather = ClimateSnapshot.WeatherState.CLEAR;
        pendingReplayDayTime = 0L;
        pendingReplayTicks = 0L;
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
        if (hasPendingReplay()) {
            tag.putString("pendingReplayAction", pendingReplayAction.name());
            tag.putString("pendingReplayWeather", pendingReplayWeather.name());
            tag.putLong("pendingReplayDayTime", pendingReplayDayTime);
            tag.putLong("pendingReplayTicks", pendingReplayTicks);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        lastAppliedGameTime = tag.getLong("lastAppliedGameTime");
        lastSeenGameTime = tag.getLong("lastSeenGameTime");
        lastBurstGameTime = Math.max(0L, Math.min(tag.getLong("lastBurstGameTime"), lastSeenGameTime));
        burstTicks = tag.getInt("burstTicks");
        pendingReplayAction = replayAction(tag.getString("pendingReplayAction"));
        pendingReplayWeather = weatherState(tag.getString("pendingReplayWeather"));
        pendingReplayDayTime = Math.max(0L, tag.getLong("pendingReplayDayTime"));
        pendingReplayTicks = Math.max(0L, tag.getLong("pendingReplayTicks"));
        if (!hasPendingReplay()) {
            clearPendingReplay();
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
}
