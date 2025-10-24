package com.evalkit.framework.common.thread;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class OrderedDispatcher<T> {
    /* ---------------- 配置项 ---------------- */
    private final int loopCount;
    private final int queueCapacity;
    private final long keepAliveNanos;
    private final RejectedHandler<T> rejectedHandler;
    private final KeyExtractor<T> keyExtractor;
    private final TaskExecutor<T> taskExecutor;

    /* ---------------- 运行时 ---------------- */
    private final List<EventLoop> loops;
    private final ConcurrentHashMap<String, EventLoop> routeCache = new ConcurrentHashMap<>();

    /* ---------------- 指标 ---------------- */
    private final AtomicLong submitCounter = new AtomicLong();
    private final AtomicLong completeCounter = new AtomicLong();

    private volatile boolean shutdown = false;

    /* ********************************************************************* */

    private OrderedDispatcher(Builder<T> builder) {
        this.loopCount = builder.loopCount;
        this.queueCapacity = builder.queueCapacity;
        this.keepAliveNanos = builder.keepAliveNanos;
        this.rejectedHandler = builder.rejectedHandler;
        this.keyExtractor = builder.keyExtractor;
        this.taskExecutor = builder.taskExecutor;
        this.loops = new ArrayList<>(loopCount);
        for (int i = 0; i < loopCount; i++) {
            EventLoop l = new EventLoop("ordered-loop-" + i);
            loops.add(l);
            l.start();
        }
    }

    /* ---------------- 提交接口 ---------------- */
    public void submit(T task) {
        if (shutdown) throw new IllegalStateException("dispatcher already shutdown");
        String key = keyExtractor.getKey(task);
        EventLoop loop = routeCache.computeIfAbsent(key, k -> loops.get(Math.abs(k.hashCode() % loopCount)));
        boolean offered = loop.offer(task);
        if (!offered) {
            rejectedHandler.onRejected(task, this);
        }
        submitCounter.incrementAndGet();
    }

    /* ---------------- 优雅关闭 ---------------- */
    public void shutdown() {
        shutdown = true;
        for (EventLoop l : loops) l.shutdown();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        for (EventLoop l : loops) {
            long left = deadline - System.nanoTime();
            if (left <= 0) return false;
            l.join(TimeUnit.NANOSECONDS.toMillis(left));
        }
        return true;
    }

    /* ---------------- 指标 ---------------- */
    public long getSubmittedTasks() {
        return submitCounter.get();
    }

    public long getCompletedTasks() {
        return completeCounter.get();
    }

    /* ---------------- Builder ---------------- */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private int loopCount = Runtime.getRuntime().availableProcessors();
        private int queueCapacity = 1 << 16;
        private long keepAliveNanos = TimeUnit.SECONDS.toNanos(60);
        private RejectedHandler<T> rejectedHandler = (t, d) -> {
            throw new RejectedExecutionException("queue full");
        };
        private KeyExtractor<T> keyExtractor;
        private TaskExecutor<T> taskExecutor;

        public Builder<T> loopCount(int n) {
            this.loopCount = n;
            return this;
        }

        public Builder<T> queueCapacity(int n) {
            this.queueCapacity = n;
            return this;
        }

        public Builder<T> keepAlive(long t, TimeUnit u) {
            this.keepAliveNanos = u.toNanos(t);
            return this;
        }

        public Builder<T> rejectedHandler(RejectedHandler<T> h) {
            this.rejectedHandler = h;
            return this;
        }

        public Builder<T> keyExtractor(KeyExtractor<T> e) {
            this.keyExtractor = e;
            return this;
        }

        public Builder<T> taskExecutor(TaskExecutor<T> e) {
            this.taskExecutor = e;
            return this;
        }

        public OrderedDispatcher<T> build() {
            if (keyExtractor == null || taskExecutor == null)
                throw new IllegalArgumentException("keyExtractor & taskExecutor must be set");
            return new OrderedDispatcher<>(this);
        }
    }

    /* ---------------- 内部 EventLoop ---------------- */
    private final class EventLoop extends Thread {
        private final BlockingQueue<T> queue = new LinkedBlockingQueue<>(queueCapacity);

        EventLoop(String name) {
            super(name);
        }

        boolean offer(T t) {
            return queue.offer(t);
        }

        void shutdown() {
            interrupt();
        }

        @Override
        public void run() {
            while (!shutdown || !queue.isEmpty()) {
                try {
                    T t = queue.poll(keepAliveNanos, TimeUnit.NANOSECONDS);
                    // 空闲超时
                    if (t == null) continue;
                    taskExecutor.execute(t);
                    completeCounter.incrementAndGet();
                } catch (InterruptedException ignored) {
                    // 被中断后继续把剩余任务清掉
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /* ---------------- 背压策略 ---------------- */
    @FunctionalInterface
    public interface RejectedHandler<T> {
        void onRejected(T task, OrderedDispatcher<T> dispatcher);
    }
}
