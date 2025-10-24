package com.evalkit.framework.common.thread;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.IntToLongFunction;
import java.util.stream.Collectors;

/**
 * 任务批量运行
 */
@Slf4j
public class BatchRunner {
    private BatchRunner() {
    }

    /**
     * 批量执行任务
     *
     * @param data            待处理数据
     * @param task            任务执行器
     * @param poolName        线程池名称
     * @param threadNum       线程数量
     * @param timeoutComputer 超时时间计算器
     * @param <T>             输入类型
     * @param <R>             输出类型
     * @return 执行结果
     */
    public static <T, R> List<R> runBatch(List<T> data,
                                          Function<T, R> task,
                                          PoolName poolName,
                                          int threadNum,
                                          IntToLongFunction timeoutComputer) {
        if (CollectionUtils.isEmpty(data)) {
            return Collections.emptyList();
        }

        ThreadPoolExecutor pool = ThreadPoolManager.get(poolName);
        int oldCore = pool.getCorePoolSize();
        int oldMax = pool.getMaximumPoolSize();

        // 1. 先扩大 maximum，再调整 core
        ThreadPoolManager.resize(poolName, threadNum, Math.max(threadNum, oldMax));

        List<CompletableFuture<R>> futures = data.stream()
                .map(item -> CompletableFuture.supplyAsync(() -> task.apply(item), pool))
                .collect(Collectors.toList());

        try {
            CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            // Java8 没有 orTimeout，可以用 get(timeout, unit) 实现
            all.get(timeoutComputer.applyAsLong(data.size()), TimeUnit.SECONDS);

            return futures.stream()
                    .map(f -> {
                        try {
                            return f.get();
                        } catch (Exception e) {
                            log.error("Future get error", e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            futures.forEach(f -> f.cancel(true));
            log.error("Batch runner error", ex);
            return null;
        } finally {
            // 还原线程池
            ThreadPoolManager.resize(poolName, oldCore, oldMax);
        }
    }
}
