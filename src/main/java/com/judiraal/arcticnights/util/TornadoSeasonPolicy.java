package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsFeatures;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import sereneseasons.api.season.ISeasonState;
import sereneseasons.api.season.SeasonHelper;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TornadoSeasonPolicy {
    private static final long MINECRAFT_DAY_TICKS = 24_000L;
    private static final List<String> SUB_SEASONS = List.of(
            "EARLY_SPRING", "MID_SPRING", "LATE_SPRING",
            "EARLY_SUMMER", "MID_SUMMER", "LATE_SUMMER",
            "EARLY_AUTUMN", "MID_AUTUMN", "LATE_AUTUMN",
            "EARLY_WINTER", "MID_WINTER", "LATE_WINTER");
    private static final Set<String> VALID_SUB_SEASONS = Set.copyOf(SUB_SEASONS);

    private TornadoSeasonPolicy() {
    }

    public static boolean isValidSubSeasonName(String name) {
        return name != null && VALID_SUB_SEASONS.contains(normalizeSubSeasonName(name));
    }

    public static boolean appliesToStorm(boolean landStorm, boolean naturallySpawned, boolean weatherMachineControlled) {
        return landStorm && naturallySpawned && !weatherMachineControlled;
    }

    public static void observeCalendar(MinecraftServer server) {
        if (!ArcticNightsFeatures.tornadoSeasonEnabled()) return;
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null || level.getGameTime() % 20L != 0L) return;

        long requiredCalendarTicks = saturatedMultiply(
                seasonCycleTicks(level),
                ArcticNightsFeatures.tornadoGraceYears());
        TornadoYearState.get(level).graceComplete(level, requiredCalendarTicks);
    }

    public static int adjustNaturalLandMaxIntensity(
            ServerLevel level,
            int originalMaxIntensity,
            int tornadoStartStage,
            int deniedMaxStage
    ) {
        if (!ArcticNightsFeatures.tornadoSeasonEnabled()
                || !ArcticNights.appliesTo(level)
                || originalMaxIntensity < tornadoStartStage) {
            return originalMaxIntensity;
        }

        int graceYears = ArcticNightsFeatures.tornadoGraceYears();
        long requiredCalendarTicks = saturatedMultiply(seasonCycleTicks(level), graceYears);
        boolean graceComplete = TornadoYearState.get(level).graceComplete(level, requiredCalendarTicks);
        boolean seasonAllowed = ArcticNightsFeatures.tornadoAllowedSubSeasons()
                .contains(currentSubSeasonName(level));
        double chanceMultiplier = ArcticNightsFeatures.tornadoNaturalChanceMultiplier();
        double chanceRoll = chanceMultiplier >= 1.0D ? 0.0D : level.random.nextDouble();

        return adjustMaxIntensity(
                originalMaxIntensity,
                tornadoStartStage,
                deniedMaxStage,
                graceComplete,
                seasonAllowed,
                chanceMultiplier,
                chanceRoll);
    }

    static int adjustMaxIntensity(
            int originalMaxIntensity,
            int tornadoStartStage,
            int deniedMaxStage,
            boolean graceComplete,
            boolean seasonAllowed,
            double chanceMultiplier,
            double chanceRoll
    ) {
        if (originalMaxIntensity < tornadoStartStage) {
            return originalMaxIntensity;
        }
        if (!graceComplete || !seasonAllowed || chanceRoll >= chanceMultiplier) {
            return Math.min(originalMaxIntensity, deniedMaxStage);
        }
        return originalMaxIntensity;
    }

    static long saturatedMultiply(long value, int multiplier) {
        if (value <= 0L || multiplier <= 0) return 0L;
        if (value > Long.MAX_VALUE / multiplier) return Long.MAX_VALUE;
        return value * multiplier;
    }

    private static long seasonCycleTicks(ServerLevel level) {
        if (ArcticNights.SERENE_SEASONS) {
            return SereneSeasonsCompat.cycleTicks(level);
        }
        return fallbackYearDays() * MINECRAFT_DAY_TICKS;
    }

    private static String currentSubSeasonName(ServerLevel level) {
        if (ArcticNights.SERENE_SEASONS) {
            return SereneSeasonsCompat.subSeasonName(level);
        }

        long daysPerYear = fallbackYearDays();
        if (daysPerYear <= 0L) return "";
        long day = Math.floorMod(level.getDayTime() / MINECRAFT_DAY_TICKS, daysPerYear);
        int ordinal = (int) Math.min(SUB_SEASONS.size() - 1L, day * SUB_SEASONS.size() / daysPerYear);
        return SUB_SEASONS.get(ordinal);
    }

    private static long fallbackYearDays() {
        return Math.max(0L, (long) ArcticNightsFeatures.daysPerSeason() * 4L);
    }

    private static String normalizeSubSeasonName(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }

    private static final class SereneSeasonsCompat {
        private SereneSeasonsCompat() {
        }

        static long cycleTicks(ServerLevel level) {
            return Math.max(1L, state(level).getCycleDuration());
        }

        static String subSeasonName(ServerLevel level) {
            return state(level).getSubSeason().name();
        }

        private static ISeasonState state(ServerLevel level) {
            return SeasonHelper.getSeasonState(level);
        }
    }
}
