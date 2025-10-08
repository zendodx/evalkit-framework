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
        return pool;
    }

    public static void resize(PoolName name, int core, int max) {
        ThreadPoolExecutor pool = get(name);
        pool.setCorePoolSize(core);
        pool.setMaximumPoolSize(max);
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
