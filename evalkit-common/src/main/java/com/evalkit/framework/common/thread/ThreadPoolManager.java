package com.evalkit.framework.common.thread;


import java.util.Map;
import java.util.concurrent.*;

/**
 * 线程池管理器
 */
public class ThreadPoolManager {
    private static final Map<PoolName, ThreadPoolExecutor> POOLS = new ConcurrentHashMap<>();

    static {
        for (PoolName name : PoolName.values()) {
            POOLS.put(name, createDefault(name));
        }
    }

    private static ThreadPoolExecutor createDefault(PoolName name) {
        int core = Runtime.getRuntime().availableProcessors();
        int max = core * 2;
        int queue = 10000;
        return new ThreadPoolExecutor(
                core,
                max,
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(queue),
                new NamedThreadFactory(name.name().toLowerCase()),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public static ThreadPoolExecutor get(PoolName name) {
        ThreadPoolExecutor pool = POOLS.get(name);
        if (pool == null) {
            throw new IllegalArgumentException("No thread pool: " + name);
        }
        // 线程池已关闭（例如前一次 run() 调用了 shutdown），自动重建以支持复用
        if (pool.isShutdown()) {
            ThreadPoolExecutor newPool = createDefault(name);
            POOLS.put(name, newPool);
            return newPool;
        }
        return pool;
    }

    public static void resize(PoolName name, int core, int max) {
        ThreadPoolExecutor pool = get(name);
        // JDK 约束：corePoolSize 不能大于 maximumPoolSize，否则抛 IllegalArgumentException。
        // 因此需要根据新旧值的大小关系决定调用顺序：
        //   扩容（新 core > 当前 max）：先设 max，再设 core
        //   缩容（新 max < 当前 core）：先设 core，再设 max
        //   其他情况：任意顺序均安全，这里统一先设 max 再设 core
        if (core > pool.getMaximumPoolSize()) {
            pool.setMaximumPoolSize(max);
            pool.setCorePoolSize(core);
        } else {
            pool.setCorePoolSize(core);
            pool.setMaximumPoolSize(max);
        }
    }

    /**
     * 关闭线程池,默认等待30秒
     */
    public static void shutdown(PoolName name) {
        shutdown(name, 30, TimeUnit.SECONDS);
    }

    public static void shutdown(PoolName name, long waitTime, TimeUnit unit) {
        ExecutorService pool = get(name);
        pool.shutdown();
        try {
            if (!pool.awaitTermination(waitTime, unit)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 简单的命名线程工厂
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final String poolName;
        private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();
        private int threadNumber = 1;

        NamedThreadFactory(String poolName) {
            this.poolName = poolName;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = defaultFactory.newThread(r);
            t.setName(poolName + "-thread-" + threadNumber++);
            return t;
        }
    }
}
