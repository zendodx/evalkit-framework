package com.evalkit.framework.common.thread;

@FunctionalInterface
public interface KeyExtractor<T> {
    String getKey(T t);
}
