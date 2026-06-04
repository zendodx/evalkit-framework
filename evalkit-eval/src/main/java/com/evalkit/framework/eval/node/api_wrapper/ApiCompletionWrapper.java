package com.evalkit.framework.eval.node.api_wrapper;

import com.evalkit.framework.common.thread.BatchRunner;
import com.evalkit.framework.common.thread.PoolName;
import com.evalkit.framework.eval.constants.NodeNamePrefix;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.node.api_wrapper.config.ApiCompletionWrapperConfig;
import com.evalkit.framework.workflow.model.WorkflowContext;
import com.evalkit.framework.workflow.model.WorkflowNode;
import com.evalkit.framework.workflow.utils.WorkflowUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * api调用结果装饰器
 * <p>
 * 一些接口的输出形式可能不符合检查要求，需要对结果进行包装转化
 */
@Slf4j
public abstract class ApiCompletionWrapper extends WorkflowNode {

    protected ApiCompletionWrapperConfig config;

    protected ApiCompletionWrapper() {
        this(ApiCompletionWrapperConfig.builder().build());
    }

    public ApiCompletionWrapper(ApiCompletionWrapperConfig config) {
        super(WorkflowUtils.generateNodeId(NodeNamePrefix.API_COMPLETION_WRAPPER));
        this.config = config;
    }

    /**
     * 包含钩子的执行，单数据项装饰失败不影响整体运行
     */
    protected DataItem executeWrapper(DataItem dataItem) {
        try {
            beforeWrapper(dataItem);
            wrapper(dataItem);
            afterWrapper(dataItem);
            return dataItem;
        } catch (Throwable e) {
            log.error("Wrapper api completion result error, dataItem: {}", dataItem, e);
            onWrapperError(dataItem, e);
            // 装饰失败，返回原来的数据项
            return dataItem;
        }
    }

    /**
     * 装饰前钩子
     */
    protected void beforeWrapper(DataItem dataItem) {
    }

    /**
     * 对 ApiCompletionResult 进行装饰转化
     */
    protected abstract void wrapper(DataItem dataItem);

    /**
     * 装饰后钩子
     */
    protected void afterWrapper(DataItem dataItem) {
    }

    /**
     * 错误处理钩子
     */
    protected void onWrapperError(DataItem dataItem, Throwable e) {
    }

    @Override
    protected void doExecute() {
        long start = System.currentTimeMillis();
        WorkflowContext ctx = getWorkflowContext();
        List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
        if (CollectionUtils.isEmpty(dataItems)) {
            throw new EvalException("Data items is empty");
        }
        BatchRunner.runBatch(dataItems, this::executeWrapper, PoolName.API_COMPLETION, config.getThreadNum(), size -> size * SINGLE_TASK_TIMEOUT);
        log.info("Wrapper api completion result success, time cost: {}ms", System.currentTimeMillis() - start);
    }
}
