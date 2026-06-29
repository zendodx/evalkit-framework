package com.evalkit.framework.workflow.model;

import com.evalkit.framework.common.utils.random.NanoIdUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;

/**
 * 工作流节点
 */
@Slf4j
@Data
public abstract class WorkflowNode implements Callable<Object>, Cloneable {
    /* 节点id前缀 */
    protected final static String ID_PREFIX = "default-";
    /* 单任务最大超时时间（秒），作为兜底默认值，子节点可通过 NodeConfig.batchTimeoutSec 覆盖 */
    protected final static long SINGLE_TASK_TIMEOUT = 60 * 10;
    /* 并发最大线程数 */
    protected final static int MAX_THREAD_NUM = Runtime.getRuntime().availableProcessors() * 2;
    /* 工作流id,具有唯一性,默认uuid,也可自己指定 */
    private String id;
    /* 工作流上下文 */
    private WorkflowContext workflowContext;
    /* 通用节点配置，子类可在构造时注入，为 null 时使用 SINGLE_TASK_TIMEOUT 作为批处理超时 */
    private NodeConfig nodeConfig;

    public WorkflowNode() {
        this(ID_PREFIX + NanoIdUtils.random());
    }

    public WorkflowNode(String id) {
        this.id = id;
    }

    /**
     * 获取批处理单条超时时间（秒），供 BatchRunner 使用。
     * <p>
     * 优先从 {@link #nodeConfig} 中读取 {@code batchTimeoutSec}；
     * 若 nodeConfig 未设置，则退回到常量 {@link #SINGLE_TASK_TIMEOUT}，保持向前兼容。
     *
     * @return 批处理单条超时秒数
     */
    protected long getBatchTimeout() {
        return nodeConfig != null ? nodeConfig.getBatchTimeoutSec() : SINGLE_TASK_TIMEOUT;
    }

    @Override
    public final Object call() throws Exception {
        doExecute();
        return null;
    }

    /**
     * 工作流节点执行
     */
    protected abstract void doExecute();

    @Override
    public WorkflowNode clone() {
        try {
            WorkflowNode clone = (WorkflowNode) super.clone();
            if (this.workflowContext != null) {
                clone.workflowContext = this.workflowContext.clone();
            }
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
