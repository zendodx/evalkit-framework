package com.evalkit.framework.common.thread;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 懒加载的有限容量资源池,用于"资源创建成本高但可安全复用"的场景
 * (如远程会话/连接/沙箱环境等),在"完全共享单资源(高复用但退化为串行)"与
 * "每个任务独立创建资源(高并发但重复付出创建成本)"之间提供折中方案:
 * 维护一个大小固定为 {@code capacity} 的资源池,懒加载按需创建(不会一次性创建满),
 * 多个并发调用方从池中获取/创建/归还资源,同一资源同一时刻只会被一个调用方持有,
 * 但不同资源之间互不阻塞、可并发使用。
 * <p>
 * 典型用法:
 * <pre>{@code
 * LazyResourcePool<String> pool = new LazyResourcePool<>(5);
 * String sessionId = pool.acquire(() -> createSession(...)); // 创建/复用
 * try {
 *     // 使用sessionId执行任务...
 * } finally {
 *     pool.release(sessionId); // 归还,供其他调用方复用
 * }
 * }</pre>
 * <p>
 * 线程安全:{@link #acquire} 基于CAS无锁地争抢"新建名额",保证并发场景下已创建的
 * 资源总数不会超过 {@code capacity};创建失败会自动归还名额,避免池子实际容量
 * 永久性小于配置值。
 *
 * @param <T> 资源类型(如Session ID、连接句柄等)
 */
@Slf4j
public class LazyResourcePool<T> {
    /* 池容量上限 */
    private final int capacity;

    /* 当前池中空闲可领用的资源 */
    private final BlockingQueue<T> idleResources;

    /* 已创建的资源数量,用于判断是否还可以新建、还是必须等待其他调用方归还 */
    private final AtomicInteger createdCount = new AtomicInteger(0);

    public LazyResourcePool(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be more than 0");
        }
        this.capacity = capacity;
        this.idleResources = new LinkedBlockingQueue<>(capacity);
    }

    /**
     * 领用一个资源:
     * <ol>
     *   <li>池中有空闲资源:直接领用(非阻塞);</li>
     *   <li>池中无空闲但未达到 {@code capacity} 上限:调用 {@code creator} 新建一个
     *       (新建失败会自动归还名额并向上抛出异常);</li>
     *   <li>池中无空闲且已达上限:阻塞等待其他调用方用完归还。</li>
     * </ol>
     *
     * @param creator 资源创建函数,仅在需要新建时调用
     * @return 领用到的资源, 调用方使用完毕后必须调用 {@link #release} 归还,
     * 否则会导致资源"泄漏"(池中可用资源逐渐减少,最终所有调用方阻塞等待)
     */
    public T acquire(ResourceCreator<T> creator) throws Exception {
        T resource = idleResources.poll();
        if (resource != null) {
            return resource;
        }
        if (tryAcquireCreationSlot()) {
            try {
                return creator.create();
            } catch (Exception e) {
                createdCount.decrementAndGet();
                throw e;
            }
        }
        // 未抢到新建名额(池已达到capacity上限),阻塞等待其他调用方用完归还
        try {
            return idleResources.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    /**
     * 归还一个资源回池中,供其他调用方复用。
     * <p>
     * 只应归还通过 {@link #acquire} 领用出去的资源,不应重复归还同一个资源,
     * 否则会导致池中"逻辑资源数"超过实际创建数量。
     */
    public void release(T resource) {
        if (resource == null) {
            return;
        }
        if (!idleResources.offer(resource)) {
            // 理论上不会发生(归还数不会超过acquire领用数,即不会超过capacity),
            // 仅做兜底日志,避免调用方误用导致资源"凭空消失"
            log.error("[LazyResourcePool] Release resource failed(pool full unexpectedly), resource={}", resource);
        }
    }

    /**
     * 当前池容量上限
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * 当前已创建的资源总数(含正在被占用、未归还的)
     */
    public int getCreatedCount() {
        return createdCount.get();
    }

    /**
     * 基于CAS无锁地争抢一个"新建资源"名额,保证并发场景下已创建的资源总数
     * 不会超过 {@code capacity}。
     *
     * @return {@code true} 表示抢到名额,调用方应立即创建一个新资源;
     * {@code false} 表示名额已被抢完,调用方应改为阻塞等待池中归还
     */
    private boolean tryAcquireCreationSlot() {
        int current;
        do {
            current = createdCount.get();
            if (current >= capacity) {
                return false;
            }
        } while (!createdCount.compareAndSet(current, current + 1));
        return true;
    }

    /**
     * 资源创建函数,允许抛出受检异常(资源创建通常涉及网络调用)
     */
    @FunctionalInterface
    public interface ResourceCreator<T> {
        T create() throws Exception;
    }
}