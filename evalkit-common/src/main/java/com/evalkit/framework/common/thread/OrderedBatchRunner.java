package com.evalkit.framework.common.thread;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.IntToLongFunction;
import java.util.stream.Collectors;

/**
 * 有序批量执行器
 */
@Slf4j
public class OrderedBatchRunner {
    private OrderedBatchRunner() {
    }

    /**
     * 有序批量执行, 同组内先来先执行
     */
    public static <T, R> List<R> runOrderedBatch(List<T> data,
                                                 Function<T, R> task,
                                                 Function<T, String> keyExtractor,
                                                 IntToLongFunction timeoutComputer) {
        // comparator为null,同组内先来先执行
        return runOrderedBatch(data, task, keyExtractor, null, timeoutComputer);
    }

    /**
     * 有序批量执行, 同组内元素排序后执行
     *
     * @param data            待处理数据
     * @param task            任务执行器
     * @param keyExtractor    key 提取器
     * @param timeoutComputer 超时时间计算器
     * @param comparator      同组内排序器
     * @param <T>             输入类型
     * @param <R>             输出类型
     * @return 执行结果
     */
    public static <T, R> List<R> runOrderedBatch(List<T> data,
                                                 Function<T, R> task,
                                                 Function<T, String> keyExtractor,
                                                 Comparator<T> comparator,
                                                 IntToLongFunction timeoutComputer) {
        if (CollectionUtils.isEmpty(data)) {
            return Collections.emptyList();
        }

        int loopCount = Runtime.getRuntime().availableProcessors();
        long timeoutSeconds = timeoutComputer.applyAsLong(data.size());

        // 准备收集器：保证输出顺序与输入一致
        int size = data.size();
        List<R> resultBox = Arrays.asList((R[]) new Object[size]);
        CountDownLatch done = new CountDownLatch(size);

        // 构建顺序分发器
        OrderedDispatcher<OrderTask<T, R>> dispatcher =
                OrderedDispatcher.<OrderTask<T, R>>builder()
                        .loopCount(loopCount)
                        .queueCapacity(100000)
                        .keyExtractor(t -> t.key)
                        .taskExecutor(t -> {
                            try {
                                R r = task.apply(t.raw);
                                resultBox.set(t.index, r);
                            } catch (Exception e) {
                                log.error("Ordered task error, key={}", t.key, e);
                            } finally {
                                done.countDown();
                            }
                        })
                        .build();

        // 提交任务
        if (comparator != null) {
            // 同组内自定义排序
            Map<String, List<T>> grouped = data.stream()
                    .collect(Collectors.groupingByConcurrent(keyExtractor))
                    .entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            e -> e.getValue().stream()
                                    .sorted(comparator)
                                    .collect(Collectors.toList())));
            int index = 0;
            for (List<T> group : grouped.values()) {
                for (T item : group) {
                    dispatcher.submit(new OrderTask<>(item, keyExtractor.apply(item), index++));
                }
            }
        } else {
            // 同组内先来的先处理
            for (int i = 0; i < size; i++) {
                T item = data.get(i);
                dispatcher.submit(new OrderTask<>(item, keyExtractor.apply(item), i));
            }
        }

        // 等待完成
        try {
            boolean ok = done.await(timeoutSeconds, TimeUnit.SECONDS);
            if (!ok) log.warn("OrderedBatchRunner timeout");
        } catch (InterruptedException e) {
            log.error("Interrupted", e);
            Thread.currentThread().interrupt();
        } finally {
            dispatcher.shutdown();
        }

        // 过滤 null（失败的任务）
        return resultBox.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
    }

    /* 包装原始对象 + key + index */
    private static class OrderTask<T, R> {
        final T raw;
        final String key;
        final int index;

        OrderTask(T raw, String key, int index) {
            this.raw = raw;
            this.key = key;
            this.index = index;
        }
    }
}
