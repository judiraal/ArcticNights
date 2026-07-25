package com.judiraal.arcticnights.test;

public final class FixtureSupport {
    private FixtureSupport() {
    }

    public static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
