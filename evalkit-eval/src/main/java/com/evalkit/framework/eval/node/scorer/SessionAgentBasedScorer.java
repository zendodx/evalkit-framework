package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.common.thread.LazyResourcePool;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.scorer.config.SessionBasedAgentScorerConfig;
import com.evalkit.framework.eval.node.scorer.model.AgentTask;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于"会话(Session)"的AgentBasedScorer
 * <p>
 * 适用于Agent协议本身是"先开一个会话/连接,再在会话内下发一轮或多轮任务指令,
 * 会话可持续多轮对话"的场景
 * 此类协议下会话创建往往伴随较重的前置开销(Skill初始化、沙箱环境准备等),本类将
 * "会话该如何创建/复用/池化/串行化保护"这部分与具体协议无关的通用生命周期管理逻辑
 * 统一封装,业务/协议子类只需实现:
 * <ul>
 *   <li>{@link #createSession}   - 如何创建一个新会话,返回会话ID(必须实现)</li>
 *   <li>{@link #sendTaskMessage} - 如何在指定会话上下发本次任务指令(必须实现)</li>
 *   <li>{@link #onSessionAssigned} - 会话确定后、发送任务前的钩子(可选覆盖,
 *       典型用途:重置该会话对应的执行上下文,避免上一条数据的轨迹串入本次)</li>
 * </ul>
 * <p>
 * 会话管理提供三种由轻到重的模式,业务方通过 {@link SessionBasedAgentScorerConfig}
 * 按需二选一,具体语义/优先级见该配置类注释。三种模式下会话都不会自动关闭,
 * 是否需要关闭、如何关闭由具体协议子类自行决定(如覆盖 {@link #afterEval} 追加逻辑)。
 *
 * @param <S> 会话标识类型(如CatX的sessionId为String,其他协议可能是自定义句柄类型)
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
public abstract class SessionAgentBasedScorer<S> extends AgentBasedScorer {
    protected final SessionBasedAgentScorerConfig sessionConfig;

    /* config.reuseSession=true时,懒加载并跨数据复用的共享会话 */
    private final AtomicReference<S> sharedSession = new AtomicReference<>();

    /* 保证共享会话同一时刻只被一个线程使用(发消息+等待结束期间独占) */
    private final ReentrantLock sharedSessionLock = new ReentrantLock();

    /* config.sessionPoolSize>0时,懒加载的会话资源池(通用并发原语见LazyResourcePool) */
    private final LazyResourcePool<S> sessionPool;

    /* 记录当前线程本次eval()从池中领用的会话,供afterEval归还,避免跨线程/跨调用串用 */
    private final ThreadLocal<S> borrowedSession = new ThreadLocal<>();

    protected SessionAgentBasedScorer(SessionBasedAgentScorerConfig config) {
        super(config);
        this.sessionConfig = config;
        this.sessionPool = isSessionPoolEnabled() ? new LazyResourcePool<>(config.getSessionPoolSize()) : null;
    }

    /**
     * 是否启用共享会话模式(完全串行):显式开启 {@code reuseSession} 或指定了
     * {@code existingSessionId} 均视为启用。优先级低于{@link #isSessionPoolEnabled()}。
     */
    protected boolean isReuseSessionEnabled() {
        return !isSessionPoolEnabled()
                && (sessionConfig.isReuseSession() || StringUtils.isNotEmpty(sessionConfig.getExistingSessionId()));
    }

    /**
     * 是否启用会话池化模式(折中方案):{@code sessionPoolSize > 0} 且未指定
     * {@code existingSessionId}(指定了现成会话时语义上只有一个会话可用,池化没有意义,
     * 直接走完全共享模式即可)。
     */
    protected boolean isSessionPoolEnabled() {
        return sessionConfig.getSessionPoolSize() > 0 && StringUtils.isEmpty(sessionConfig.getExistingSessionId());
    }

    /**
     * 业务方通过 {@code existingSessionId} 指定了现成会话时,子类应在构造函数中
     * (完成自身协议相关初始化后)调用本方法预置为共享会话,后续所有数据都复用它、不再创建。
     * <p>
     * 之所以不在本类构造函数中自动完成,是因为 {@code existingSessionId} 通常是字符串,
     * 而会话类型 {@code S} 可能并非字符串(需要子类自行转换/包装),故交由子类决定。
     */
    protected void presetSharedSession(S session) {
        sharedSession.set(session);
    }

    // =========================================================================
    // 通用封装:提交任务(决定会话 + 发送任务指令)
    // =========================================================================

    @Override
    public final String submitAgentTask(AgentTask task) throws Exception {
        boolean pooled = isSessionPoolEnabled();
        boolean reuse = isReuseSessionEnabled();
        if (reuse) {
            // 复用会话期间同一时刻只能有一个任务在使用,加锁保证串行,
            // 在eval()流程结束后(afterEval)统一释放,避免死锁/漏释放
            sharedSessionLock.lock();
        }
        S session;
        if (pooled) {
            session = resolvePooledSession(task);
        } else if (reuse) {
            session = resolveSharedSession(task);
        } else {
            session = createSession(task);
        }
        onSessionAssigned(session, task);
        return sendTaskMessage(session, task);
    }

    /**
     * 获取本次任务使用的共享会话。若业务方指定了现成会话,构造时子类已通过
     * {@link #presetSharedSession} 预置,这里直接复用;否则首次调用时懒加载创建,此后一直复用。
     * 调用方需保证 {@link #sharedSessionLock} 已持有,以避免并发重复创建。
     */
    private S resolveSharedSession(AgentTask task) throws Exception {
        S session = sharedSession.get();
        if (session != null) {
            log.info("[SessionBasedAgentScorer] Reuse shared session, dataIndex={}, session={}",
                    task.getDataIndex(), session);
            return session;
        }
        session = createSession(task);
        sharedSession.set(session);
        return session;
    }

    /**
     * 从会话池中领用一个会话(池化折中方案),具体的领用/新建/阻塞等待逻辑由通用并发原语
     * {@link LazyResourcePool#acquire} 提供,本方法只需传入"如何新建一个会话"的回调。
     * 领用到的会话会记录到 {@link #borrowedSession}(线程本地),供 {@link #afterEval}
     * 归还回池,调用方无需自行归还。
     */
    private S resolvePooledSession(AgentTask task) throws Exception {
        S session = sessionPool.acquire(() -> {
            S created = createSession(task);
            log.info("[SessionBasedAgentScorer] Pooled session created, dataIndex={}, session={}, poolSize={}",
                    task.getDataIndex(), created, sessionConfig.getSessionPoolSize());
            return created;
        });
        borrowedSession.set(session);
        log.info("[SessionBasedAgentScorer] Borrow pooled session, dataIndex={}, session={}",
                task.getDataIndex(), session);
        return session;
    }

    /**
     * 无论eval()成功/失败/异常都会执行(见 {@code Scorer#evalWrapper}),
     * 用于兜底释放 {@link #sharedSessionLock}(避免复用会话模式下线程异常退出导致死锁),
     * 以及将池化模式下本线程领用的会话归还回会话池(避免会话泄漏、让其他等待中的worker
     * 能继续领用)。子类如需追加自身的清理逻辑,记得调用 {@code super.afterEval(...)}。
     */
    @Override
    public ScorerResult afterEval(DataItem dataItem, ScorerResult result) {
        if (isReuseSessionEnabled() && sharedSessionLock.isHeldByCurrentThread()) {
            sharedSessionLock.unlock();
        }
        if (isSessionPoolEnabled()) {
            S session = borrowedSession.get();
            if (session != null) {
                borrowedSession.remove();
                sessionPool.release(session);
            }
        }
        return super.afterEval(dataItem, result);
    }

    // =========================================================================
    // 协议相关钩子:必须由子类实现
    // =========================================================================

    /**
     * 创建一个新会话,返回会话标识。具体如何创建(调用哪个Agent协议的建会话接口)
     * 由子类根据自身协议实现决定。
     */
    protected abstract S createSession(AgentTask task) throws Exception;

    /**
     * 在指定会话上下发本次任务指令,返回本次提交后用于后续轮询的任务ID/句柄
     * (通常就是会话标识本身,但允许子类返回其他句柄,如会话+消息ID的组合)。
     */
    protected abstract String sendTaskMessage(S session, AgentTask task) throws Exception;

    // =========================================================================
    // 协议相关钩子:可选覆盖,提供默认空实现
    // =========================================================================

    /**
     * 会话确定后、发送任务指令前触发,典型用途:重置该会话对应的执行上下文
     * (避免复用/池化场景下上一条数据的轨迹串入本次)。默认空实现。
     */
    protected void onSessionAssigned(S session, AgentTask task) {
        // 默认不做任何处理
    }
}