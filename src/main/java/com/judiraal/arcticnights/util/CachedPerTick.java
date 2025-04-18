package com.judiraal.arcticnights.util;

import java.util.function.Function;
import java.util.function.Supplier;

public class CachedPerTick<T, R> {
    private final Function<T, R> function;
    private R currentValue;
    private long lastTickCount = -1;

    public CachedPerTick(Function<T, R> function) {
        this.function = function;
    }

    public static <T, R> CachedPerTick<T, R> of(Function<T, R> function) {
        return new CachedPerTick<>(function);
    }

    public R get(long tickCount, T t) {
        return get(tickCount, () -> t);
    }

    public R get(long tickCount, Supplier<T> supplier) {
        if (tickCount == lastTickCount) return currentValue;
        currentValue = function.apply(supplier.get());
        lastTickCount = tickCount;
        return currentValue;
    }
}
