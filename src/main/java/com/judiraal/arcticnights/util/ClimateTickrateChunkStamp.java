package com.judiraal.arcticnights.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class ClimateTickrateChunkStamp implements INBTSerializable<CompoundTag> {
    private long lastAppliedGameTime;
    private long lastSeenGameTime;
    private int burstTicks;

    public ClimateTickrateChunkStamp() {
    }

    public ClimateTickrateChunkStamp(long lastAppliedGameTime, long lastSeenGameTime, int burstTicks) {
        this.lastAppliedGameTime = Math.max(0L, lastAppliedGameTime);
        this.lastSeenGameTime = Math.max(0L, lastSeenGameTime);
        this.burstTicks = Math.max(0, burstTicks);
    }

    public long lastAppliedGameTime() {
        return lastAppliedGameTime;
    }

    public long lastSeenGameTime() {
        return lastSeenGameTime;
    }

    public int burstTicks() {
        return burstTicks;
    }

    public void mark(long lastAppliedGameTime, long lastSeenGameTime, int burstTicks) {
        this.lastAppliedGameTime = Math.max(0L, lastAppliedGameTime);
        this.lastSeenGameTime = Math.max(0L, lastSeenGameTime);
        this.burstTicks = Math.max(0, burstTicks);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("lastAppliedGameTime", lastAppliedGameTime);
        tag.putLong("lastSeenGameTime", lastSeenGameTime);
        tag.putInt("burstTicks", burstTicks);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        lastAppliedGameTime = tag.getLong("lastAppliedGameTime");
        lastSeenGameTime = tag.getLong("lastSeenGameTime");
        burstTicks = tag.getInt("burstTicks");
    }
}
