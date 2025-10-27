package com.evalkit.framework.eval.node.dataloader_wrapper;

import com.evalkit.framework.common.thread.BatchRunner;
import com.evalkit.framework.common.thread.PoolName;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.node.dataloader_wrapper.config.DataLoaderWrapperConfig;
import com.evalkit.framework.workflow.model.WorkflowContext;
import com.evalkit.framework.workflow.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 评测数据装饰器,可对数据进行增强操作,例如:改写,裂变,Mock等
 */
@Slf4j
public abstract class DataLoaderWrapper extends WorkflowNode {
    protected DataLoaderWrapperConfig config;

    protected DataLoaderWrapper() {
        this.config = DataLoaderWrapperConfig.builder().build();
    }

    public DataLoaderWrapper(DataLoaderWrapperConfig config) {
        this.config = config;
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
        WorkflowContext ctx = getWorkflowContext();
        List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
        BatchRunner.runBatch(dataItems, this::executeWrapper, PoolName.DATA_WRAPPER, config.getThreadNum(), size -> size * SINGLE_TASK_TIMEOUT);
        log.info("Wrapper data success, time cost: {}ms", System.currentTimeMillis() - start);
    }
}
