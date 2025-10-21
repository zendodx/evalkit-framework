package com.evalkit.framework.common.thread;

@FunctionalInterface
public interface TaskExecutor<T> {
    void execute(T t) throws Exception;
}
