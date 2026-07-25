package com.judiraal.arcticnights.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

final class TornadoYearState extends SavedData {
    private static final String DATA_NAME = "arcticnights_tornado_year";
    private static final String GRACE_COMPLETE_KEY = "grace_complete";
    private static final String MAX_CALENDAR_TIME_KEY = "max_calendar_time";

    private boolean graceComplete;
    private long maxCalendarTime;

    static TornadoYearState get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(TornadoYearState::new, TornadoYearState::load), DATA_NAME);
    }

    private static TornadoYearState load(CompoundTag tag, HolderLookup.Provider provider) {
        TornadoYearState state = new TornadoYearState();
        state.graceComplete = tag.getBoolean(GRACE_COMPLETE_KEY);
        if (tag.contains(MAX_CALENDAR_TIME_KEY, Tag.TAG_LONG)) {
            state.maxCalendarTime = Math.max(0L, tag.getLong(MAX_CALENDAR_TIME_KEY));
        }
        return state;
    }

    boolean graceComplete(ServerLevel level, long requiredCalendarTicks) {
        if (requiredCalendarTicks <= 0L || graceComplete) {
            return true;
        }

        long calendarTime = Math.max(0L, level.getDayTime());
        if (calendarTime > maxCalendarTime) {
            boolean crossedCalendarDay = calendarTime / 24_000L > maxCalendarTime / 24_000L;
            maxCalendarTime = calendarTime;
            if (crossedCalendarDay) setDirty();
        }
        if (maxCalendarTime >= requiredCalendarTicks) {
            graceComplete = true;
            setDirty();
        }
        return graceComplete;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean(GRACE_COMPLETE_KEY, graceComplete);
        tag.putLong(MAX_CALENDAR_TIME_KEY, maxCalendarTime);
        return tag;
    }
}
