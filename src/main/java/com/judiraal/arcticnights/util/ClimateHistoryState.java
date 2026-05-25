package com.judiraal.arcticnights.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

final class ClimateHistoryState extends SavedData {
    private static final String DATA_NAME = "arcticnights_climate_history";
    private static final String SPANS_KEY = "weather_spans";

    private final Deque<WeatherSpan> weatherSpans = new ArrayDeque<>();

    public static ClimateHistoryState get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(ClimateHistoryState::new, ClimateHistoryState::load), DATA_NAME);
    }

    private static ClimateHistoryState load(CompoundTag tag, HolderLookup.Provider provider) {
        ClimateHistoryState state = new ClimateHistoryState();
        ListTag spans = tag.getList(SPANS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < spans.size(); i++) {
            CompoundTag spanTag = spans.getCompound(i);
            ClimateSnapshot.WeatherState weather = weatherState(spanTag.getString("state"));
            if (weather == null) continue;
            long start = spanTag.getLong("start_day_time");
            long end = spanTag.getLong("end_day_time");
            if (end < start) continue;
            state.weatherSpans.addLast(new WeatherSpan(start, end, weather));
        }
        return state;
    }

    void tick(ServerLevel level, long maxHistoryTicks) {
        long dayTime = level.getDayTime();
        ClimateSnapshot.WeatherState current = currentWeather(level);
        WeatherSpan last = weatherSpans.peekLast();
        boolean changed = false;

        if (last != null && dayTime < last.startDayTime()) {
            weatherSpans.clear();
            weatherSpans.addLast(new WeatherSpan(dayTime, dayTime, current));
            setDirty();
            return;
        }

        if (last != null && dayTime >= last.startDayTime() && last.state() == current) {
            weatherSpans.removeLast();
            weatherSpans.addLast(new WeatherSpan(last.startDayTime(), dayTime, current));
            changed = last.endDayTime() != dayTime;
        } else {
            weatherSpans.addLast(new WeatherSpan(dayTime, dayTime, current));
            changed = true;
        }

        long keepAfter = dayTime - maxHistoryTicks;
        while (weatherSpans.size() > 1 && weatherSpans.peekFirst().endDayTime() < keepAfter) {
            weatherSpans.removeFirst();
            changed = true;
        }

        if (changed) setDirty();
    }

    WeatherSample weatherAt(long dayTime) {
        Iterator<WeatherSpan> iterator = weatherSpans.descendingIterator();
        while (iterator.hasNext()) {
            WeatherSpan span = iterator.next();
            if (dayTime >= span.startDayTime() && dayTime <= span.endDayTime()) {
                return new WeatherSample(span.state(), true);
            }
        }
        return new WeatherSample(ClimateSnapshot.WeatherState.CLEAR, false);
    }

    boolean hasPrecipitationBetween(long fromDayTime, long toDayTime) {
        long from = Math.min(fromDayTime, toDayTime);
        long to = Math.max(fromDayTime, toDayTime);
        Iterator<WeatherSpan> iterator = weatherSpans.descendingIterator();
        while (iterator.hasNext()) {
            WeatherSpan span = iterator.next();
            if (span.endDayTime() < from) break;
            if (span.startDayTime() > to) continue;
            if (isPrecipitating(span.state())) return true;
        }
        return false;
    }

    String debugWindow(long fromDayTime, long toDayTime) {
        long from = Math.min(fromDayTime, toDayTime);
        long to = Math.max(fromDayTime, toDayTime);
        int overlaps = 0;
        boolean precipitating = false;
        WeatherSpan first = null;
        WeatherSpan last = null;

        for (WeatherSpan span : weatherSpans) {
            if (span.endDayTime() < from || span.startDayTime() > to) continue;
            overlaps++;
            if (first == null) first = span;
            last = span;
            if (isPrecipitating(span.state())) precipitating = true;
        }

        WeatherSample atEnd = weatherAt(to);
        return "spans=" + weatherSpans.size()
                + ", window=" + from + ".." + to
                + ", overlap=" + overlaps
                + ", precip=" + precipitating
                + ", atEnd=" + atEnd.state().name() + (atEnd.recorded() ? "/recorded" : "/synthetic")
                + ", first=" + spanSummary(first)
                + ", last=" + spanSummary(last);
    }

    void replaceWithDebugSpan(long startDayTime, long endDayTime, ClimateSnapshot.WeatherState state) {
        long start = Math.min(startDayTime, endDayTime);
        long end = Math.max(startDayTime, endDayTime);
        weatherSpans.clear();
        weatherSpans.addLast(new WeatherSpan(start, end, state));
        setDirty();
    }

    ClimateSnapshot.WeatherState currentWeather(ServerLevel level) {
        if (level.isThundering()) return ClimateSnapshot.WeatherState.THUNDER;
        if (level.isRaining()) return ClimateSnapshot.WeatherState.RAIN;
        return ClimateSnapshot.WeatherState.CLEAR;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag spans = new ListTag();
        for (WeatherSpan span : weatherSpans) {
            CompoundTag spanTag = new CompoundTag();
            spanTag.putLong("start_day_time", span.startDayTime());
            spanTag.putLong("end_day_time", span.endDayTime());
            spanTag.putString("state", span.state().name());
            spans.add(spanTag);
        }
        tag.put(SPANS_KEY, spans);
        return tag;
    }

    private static boolean isPrecipitating(ClimateSnapshot.WeatherState state) {
        return state == ClimateSnapshot.WeatherState.RAIN || state == ClimateSnapshot.WeatherState.THUNDER;
    }

    private static String spanSummary(WeatherSpan span) {
        if (span == null) return "none";
        return span.state().name() + "[" + span.startDayTime() + ".." + span.endDayTime() + "]";
    }

    private static ClimateSnapshot.WeatherState weatherState(String name) {
        try {
            return ClimateSnapshot.WeatherState.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    record WeatherSample(ClimateSnapshot.WeatherState state, boolean recorded) {
    }

    private record WeatherSpan(long startDayTime, long endDayTime, ClimateSnapshot.WeatherState state) {
    }
}
