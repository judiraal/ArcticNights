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
    private static final String START_GAME_TIME_KEY = "start_game_time";
    private static final String END_GAME_TIME_KEY = "end_game_time";
    private static final String START_DAY_TIME_KEY = "start_day_time";
    private static final String END_DAY_TIME_KEY = "end_day_time";

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
            if (!spanTag.contains(START_GAME_TIME_KEY, Tag.TAG_LONG)
                    || !spanTag.contains(END_GAME_TIME_KEY, Tag.TAG_LONG)
                    || !spanTag.contains(START_DAY_TIME_KEY, Tag.TAG_LONG)
                    || !spanTag.contains(END_DAY_TIME_KEY, Tag.TAG_LONG)) continue;
            long startGameTime = spanTag.getLong(START_GAME_TIME_KEY);
            long endGameTime = spanTag.getLong(END_GAME_TIME_KEY);
            long startDayTime = spanTag.getLong(START_DAY_TIME_KEY);
            long endDayTime = spanTag.getLong(END_DAY_TIME_KEY);
            if (endGameTime < startGameTime) continue;
            state.weatherSpans.addLast(new WeatherSpan(startGameTime, endGameTime, startDayTime, endDayTime, weather));
        }
        return state;
    }

    void tick(ServerLevel level, long maxHistoryTicks) {
        long gameTime = level.getGameTime();
        long dayTime = level.getDayTime();
        ClimateSnapshot.WeatherState current = currentWeather(level);
        WeatherSpan last = weatherSpans.peekLast();
        boolean changed = false;

        if (last != null && gameTime < last.startGameTime()) {
            weatherSpans.clear();
            weatherSpans.addLast(new WeatherSpan(gameTime, gameTime, dayTime, dayTime, current));
            setDirty();
            return;
        }

        if (last != null && gameTime >= last.startGameTime() && last.state() == current && isContinuousDayTime(last, gameTime, dayTime)) {
            weatherSpans.removeLast();
            weatherSpans.addLast(new WeatherSpan(last.startGameTime(), gameTime, last.startDayTime(), dayTime, current));
            changed = last.endGameTime() != gameTime || last.endDayTime() != dayTime;
        } else {
            weatherSpans.addLast(new WeatherSpan(gameTime, gameTime, dayTime, dayTime, current));
            changed = true;
        }

        long keepAfter = gameTime - maxHistoryTicks;
        while (weatherSpans.size() > 1 && weatherSpans.peekFirst().endGameTime() < keepAfter) {
            weatherSpans.removeFirst();
            changed = true;
        }

        if (changed) setDirty();
    }

    WeatherSample weatherAt(long gameTime) {
        Iterator<WeatherSpan> iterator = weatherSpans.descendingIterator();
        while (iterator.hasNext()) {
            WeatherSpan span = iterator.next();
            if (gameTime >= span.startGameTime() && gameTime <= span.endGameTime()) {
                return new WeatherSample(span.state(), true);
            }
        }
        return new WeatherSample(ClimateSnapshot.WeatherState.CLEAR, false);
    }

    boolean hasPrecipitationBetween(long fromGameTime, long toGameTime) {
        return precipitationTicksBetween(fromGameTime, toGameTime) > 0L;
    }

    long precipitationTicksBetween(long fromGameTime, long toGameTime) {
        long from = Math.min(fromGameTime, toGameTime);
        long to = Math.max(fromGameTime, toGameTime);
        long ticks = 0L;
        Iterator<WeatherSpan> iterator = weatherSpans.descendingIterator();
        while (iterator.hasNext()) {
            WeatherSpan span = iterator.next();
            if (span.endGameTime() < from) break;
            if (span.startGameTime() > to) continue;
            if (!isPrecipitating(span.state())) continue;
            long overlapStart = Math.max(from, span.startGameTime());
            long overlapEnd = Math.min(to, span.endGameTime());
            ticks += Math.max(0L, overlapEnd - overlapStart);
        }
        return ticks;
    }

    void visitSpansBetween(long fromGameTime, long toGameTime, WeatherSpanVisitor visitor) {
        long from = Math.min(fromGameTime, toGameTime);
        long to = Math.max(fromGameTime, toGameTime);
        for (WeatherSpan span : weatherSpans) {
            if (span.endGameTime() < from || span.startGameTime() > to) continue;
            long overlapStart = Math.max(from, span.startGameTime());
            long overlapEnd = Math.min(to, span.endGameTime());
            if (overlapEnd <= overlapStart) continue;
            visitor.accept(overlapStart, overlapEnd, span.sampleDayTime(overlapStart, overlapEnd), span.state());
        }
    }

    String debugWindow(long fromGameTime, long toGameTime) {
        long from = Math.min(fromGameTime, toGameTime);
        long to = Math.max(fromGameTime, toGameTime);
        int overlaps = 0;
        boolean precipitating = false;
        WeatherSpan first = null;
        WeatherSpan last = null;

        for (WeatherSpan span : weatherSpans) {
            if (span.endGameTime() < from || span.startGameTime() > to) continue;
            overlaps++;
            if (first == null) first = span;
            last = span;
            if (isPrecipitating(span.state())) precipitating = true;
        }

        WeatherSample atEnd = weatherAt(to);
        return "spans=" + weatherSpans.size()
                + ", gameWindow=" + from + ".." + to
                + ", overlap=" + overlaps
                + ", precip=" + precipitating
                + ", precipTicks=" + precipitationTicksBetween(from, to)
                + ", atEnd=" + atEnd.state().name() + (atEnd.recorded() ? "/recorded" : "/synthetic")
                + ", first=" + spanSummary(first)
                + ", last=" + spanSummary(last);
    }

    void replaceWithDebugSpan(long startGameTime, long endGameTime, ClimateSnapshot.WeatherState state) {
        long start = Math.min(startGameTime, endGameTime);
        long end = Math.max(startGameTime, endGameTime);
        weatherSpans.clear();
        weatherSpans.addLast(new WeatherSpan(start, end, start, end, state));
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
            spanTag.putLong(START_GAME_TIME_KEY, span.startGameTime());
            spanTag.putLong(END_GAME_TIME_KEY, span.endGameTime());
            spanTag.putLong(START_DAY_TIME_KEY, span.startDayTime());
            spanTag.putLong(END_DAY_TIME_KEY, span.endDayTime());
            spanTag.putString("state", span.state().name());
            spans.add(spanTag);
        }
        tag.put(SPANS_KEY, spans);
        return tag;
    }

    private static boolean isContinuousDayTime(WeatherSpan last, long gameTime, long dayTime) {
        long gameElapsed = Math.max(0L, gameTime - last.endGameTime());
        long dayElapsed = dayTime - last.endDayTime();
        return dayElapsed >= 0L && dayElapsed <= gameElapsed + 1L;
    }

    private static boolean isPrecipitating(ClimateSnapshot.WeatherState state) {
        return state == ClimateSnapshot.WeatherState.RAIN || state == ClimateSnapshot.WeatherState.THUNDER;
    }

    private static String spanSummary(WeatherSpan span) {
        if (span == null) return "none";
        return span.state().name()
                + "[game=" + span.startGameTime() + ".." + span.endGameTime()
                + ",day=" + span.startDayTime() + ".." + span.endDayTime() + "]";
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

    interface WeatherSpanVisitor {
        void accept(long startGameTime, long endGameTime, long sampleDayTime, ClimateSnapshot.WeatherState state);
    }

    private record WeatherSpan(long startGameTime, long endGameTime, long startDayTime, long endDayTime, ClimateSnapshot.WeatherState state) {
        private long sampleDayTime(long overlapStartGameTime, long overlapEndGameTime) {
            long spanTicks = Math.max(1L, endGameTime - startGameTime);
            long overlapMidpoint = overlapStartGameTime + Math.max(0L, overlapEndGameTime - overlapStartGameTime) / 2L;
            double progress = Math.max(0.0D, Math.min(1.0D, (overlapMidpoint - startGameTime) / (double) spanTicks));
            return Math.max(0L, Math.round(startDayTime + (endDayTime - startDayTime) * progress));
        }
    }
}
