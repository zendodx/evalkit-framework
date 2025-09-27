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
public abstract class WorkflowNode implements Callable<Object> {
    // 节点id前缀
    protected final static String ID_PREFIX = "default-";
    // 单任务最大超时时间
    protected final static long SINGLE_TASK_TIMEOUT = 60 * 10;
    // 并发最大线程数
    protected final static int MAX_THREAD_NUM = Runtime.getRuntime().availableProcessors() * 2;
    // 工作流id,具有唯一性,默认uuid,也可自己指定
    private String id;

    public WorkflowNode() {
        this(ID_PREFIX + NanoIdUtils.random());
    }

    public WorkflowNode(String id) {
        this.id = id;
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
}
