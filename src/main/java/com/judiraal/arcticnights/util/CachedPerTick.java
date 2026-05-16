package com.judiraal.arcticnights.util;

import java.util.function.Function;
import java.util.function.Supplier;

public class CachedPerTick<T, R> {
    private final Function<T, R> function;
    private T currentInput;
    private R currentValue;
    private long lastTickCount = -1;

    public CachedPerTick(Function<T, R> function) {
        this.function = function;
    }

    public static <T, R> CachedPerTick<T, R> of(Function<T, R> function) {
        return new CachedPerTick<>(function);
    }

    public R get(long tickCount, T t) {
        if (tickCount == lastTickCount && currentInput == t) return currentValue;
        currentInput = t;
        currentValue = function.apply(t);
        lastTickCount = tickCount;
        return currentValue;
    }

    public R get(long tickCount, Supplier<T> supplier) {
        if (tickCount == lastTickCount) return currentValue;
        currentInput = supplier.get();
        currentValue = function.apply(currentInput);
        lastTickCount = tickCount;
        return currentValue;
    }
}
