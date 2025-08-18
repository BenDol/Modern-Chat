package com.modernchat.common;

import java.util.function.Supplier;

public class LazyLoad<T>
{
    private T value;

    private final Supplier<T> supplier;

    public LazyLoad(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public T get() {
        if (value == null) {
            value = supplier.get();
        }
        return value;
    }

    public boolean isLoaded() {
        return value != null;
    }

    public void reset() {
        value = null;
    }

}
