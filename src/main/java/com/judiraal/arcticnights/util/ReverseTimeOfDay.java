package com.judiraal.arcticnights.util;

import net.minecraft.util.Mth;

import java.util.Arrays;

public class ReverseTimeOfDay {
    private static final float[] TIME_OF_DAY;

    static {
        float[] table = new float[24000];
        for (int i=6000; i<30000; i++) table[i-6000] = calcTimeOfDay(i);
        TIME_OF_DAY = table;
    }

    private static float calcTimeOfDay(int ticks) {
        double d0 = Mth.frac(ticks / 24000.0 - 0.25);
        double d1 = 0.5 - Math.cos(d0 * Math.PI) / 2.0;
        return (float)(d0 * 2.0 + d1) / 3.0F;
    }

    public static float getTimeOfDay(int ticks) {
        return TIME_OF_DAY[(ticks + 18000) % 24000];
    }

    public static int reverseTimeOfDay(float timeOfDay) {
        return (Math.abs(Arrays.binarySearch(TIME_OF_DAY, timeOfDay)) + 6000) % 24000;
    }
}
