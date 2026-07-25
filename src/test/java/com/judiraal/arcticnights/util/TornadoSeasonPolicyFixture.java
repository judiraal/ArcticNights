package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.test.FixtureSupport;

import java.util.Random;

public final class TornadoSeasonPolicyFixture {
    private static final int HAIL = 3;
    private static final int F0 = 4;
    private static final int F5 = 9;

    private TornadoSeasonPolicyFixture() {
    }

    public static void run() {
        checkSubSeasonNames();
        checkStormOwnership();
        checkGraceAndSeasonGates();
        checkChanceBoundaryAndSeverity();
        checkHalfRate();
        checkSaturatedYearDuration();
    }

    private static void checkStormOwnership() {
        FixtureSupport.check(TornadoSeasonPolicy.appliesToStorm(true, true, false),
                "natural land storm should use the policy");
        FixtureSupport.check(!TornadoSeasonPolicy.appliesToStorm(false, true, false),
                "water cyclone should bypass the policy");
        FixtureSupport.check(!TornadoSeasonPolicy.appliesToStorm(true, false, false),
                "non-natural storm should bypass the policy");
        FixtureSupport.check(!TornadoSeasonPolicy.appliesToStorm(true, true, true),
                "weather-machine-controlled storm should bypass the policy");
    }

    private static void checkSubSeasonNames() {
        FixtureSupport.check(TornadoSeasonPolicy.isValidSubSeasonName("EARLY_SPRING"), "early spring should be valid");
        FixtureSupport.check(TornadoSeasonPolicy.isValidSubSeasonName(" late_winter "), "sub-season validation should normalize input");
        FixtureSupport.check(!TornadoSeasonPolicy.isValidSubSeasonName("SPRING"), "whole-season shorthand should not be accepted");
        FixtureSupport.check(!TornadoSeasonPolicy.isValidSubSeasonName(null), "null sub-season should not be accepted");
    }

    private static void checkGraceAndSeasonGates() {
        FixtureSupport.check(adjust(HAIL, false, false, 0.0D, 1.0D) == HAIL,
                "ordinary hail should remain unchanged during the grace period");
        FixtureSupport.check(adjust(F5, false, true, 0.5D, 0.0D) == HAIL,
                "first-year tornado should be capped to hail");
        FixtureSupport.check(adjust(F5, true, false, 0.5D, 0.0D) == HAIL,
                "out-of-season tornado should be capped to hail");
        FixtureSupport.check(adjust(F0, true, true, 0.5D, 0.25D) == F0,
                "eligible accepted F0 should remain F0");
    }

    private static void checkChanceBoundaryAndSeverity() {
        FixtureSupport.check(adjust(F5, true, true, 0.5D, 0.499999D) == F5,
                "roll below multiplier should preserve original tornado severity");
        FixtureSupport.check(adjust(F5, true, true, 0.5D, 0.5D) == HAIL,
                "roll at multiplier boundary should be rejected");
        FixtureSupport.check(adjust(F5, true, true, 0.0D, 0.0D) == HAIL,
                "zero multiplier should reject every tornado");
        FixtureSupport.check(adjust(F5, true, true, 1.0D, Math.nextDown(1.0D)) == F5,
                "one multiplier should accept every valid random roll");
    }

    private static void checkHalfRate() {
        Random random = new Random(0xA11CE5EEDL);
        int accepted = 0;
        int samples = 10_000;
        for (int i = 0; i < samples; i++) {
            if (adjust(F0, true, true, 0.5D, random.nextDouble()) == F0) {
                accepted++;
            }
        }
        FixtureSupport.check(Math.abs(accepted - samples / 2) < 200,
                "half multiplier should retain approximately half the tornado rolls, got " + accepted);
    }

    private static void checkSaturatedYearDuration() {
        FixtureSupport.check(TornadoSeasonPolicy.saturatedMultiply(2_304_000L, 1) == 2_304_000L,
                "one Serene year should remain 2,304,000 ticks");
        FixtureSupport.check(TornadoSeasonPolicy.saturatedMultiply(2_304_000L, 0) == 0L,
                "zero grace years should require zero ticks");
        FixtureSupport.check(TornadoSeasonPolicy.saturatedMultiply(Long.MAX_VALUE, 2) == Long.MAX_VALUE,
                "year duration multiplication should saturate");
    }

    private static int adjust(
            int original,
            boolean graceComplete,
            boolean seasonAllowed,
            double multiplier,
            double roll
    ) {
        return TornadoSeasonPolicy.adjustMaxIntensity(
                original,
                F0,
                HAIL,
                graceComplete,
                seasonAllowed,
                multiplier,
                roll);
    }
}
