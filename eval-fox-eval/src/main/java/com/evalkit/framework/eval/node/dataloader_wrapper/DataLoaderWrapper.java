package com.evalkit.framework.eval.node.dataloader_wrapper;

import com.evalkit.framework.eval.constants.NodeNamePrefix;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.common.thread.BatchRunner;
import com.evalkit.framework.common.thread.PoolName;
import com.evalkit.framework.workflow.WorkflowContextHolder;
import com.evalkit.framework.workflow.model.WorkflowContext;
import com.evalkit.framework.workflow.model.WorkflowNode;
import com.evalkit.framework.workflow.utils.WorkflowUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 评测数据装饰器,可对数据进行增强操作,例如:改写,裂变,Mock等
 */
@Slf4j
public abstract class DataLoaderWrapper extends WorkflowNode {
    // 单任务执行超时时间
    protected final static long SINGLE_TASK_TIMEOUT = 60 * 10;
    /* 最大线程数 */
    protected final static int MAX_THREAD_NUM = Runtime.getRuntime().availableProcessors() * 2;
    /* 处理线程 */
    protected int threadNum;

    public DataLoaderWrapper() {
        this(1);
    }

    public DataLoaderWrapper(int threadNum) {
        super(WorkflowUtils.generateNodeId(NodeNamePrefix.DATA_LOADER_WRAPPER));
        this.threadNum = getRealThreadNum(threadNum);
    }

    public void setThreadNum(int threadNum) {
        this.threadNum = getRealThreadNum(threadNum);
    }

    private int getRealThreadNum(int threadNum) {
        if (threadNum <= 0) {
            return 1;
        }
        return Math.min(threadNum, MAX_THREAD_NUM);
    }

    /**
     * 包含钩子的执行
     */
    protected DataItem executeWrapper(DataItem dataItem) {
        try {
            beforeWrapper(dataItem);
            wrapper(dataItem);
            afterWrapper(dataItem);
            return dataItem;
        } catch (Throwable e) {
            log.error("Wrapper data error, dataItem: {}", dataItem, e);
            onWrapperError(dataItem, e);
            // 装饰失败,返回原来的数据项
            return dataItem;
        }
    }

    /**
     * 装饰前钩子
     */
    protected void beforeWrapper(DataItem dataItem) {
    }

    /**
     * 装饰
     */
    protected abstract void wrapper(DataItem dataItem);

    /**
     * 装饰后钩子
     */
    protected void afterWrapper(DataItem dataItems) {
    }

    /**
     * 错误处理钩子
     */
    protected void onWrapperError(DataItem dataItem, Throwable e) {
    }

    @Override
    public void doExecute() {
        long start = System.currentTimeMillis();
        WorkflowContext ctx = WorkflowContextHolder.get();
        List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
        BatchRunner.runBatch(dataItems, this::executeWrapper, PoolName.DATA_WRAPPER, threadNum, size -> size * SINGLE_TASK_TIMEOUT);
        log.info("Wrapper data success, time cost: {}ms", System.currentTimeMillis() - start);
    }
}
