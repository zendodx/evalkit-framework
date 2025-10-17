package com.evalkit.framework.eval.node.counter;

import com.evalkit.framework.eval.constants.NodeNamePrefix;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.model.CountResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.workflow.model.WorkflowContext;
import com.evalkit.framework.workflow.model.WorkflowNode;
import com.evalkit.framework.workflow.utils.WorkflowUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * 统计器
 */
@Slf4j
public abstract class Counter extends WorkflowNode {
    public Counter() {
        super(WorkflowUtils.generateNodeId(NodeNamePrefix.COUNTER));
    }

    /**
     * 统计器生成周期方法
     */
    protected CountResult countWrapper(List<DataItem> dataItems) {
        beforeCount(dataItems);
        try {
            CountResult countResult = count(dataItems);
            return afterCount(dataItems, countResult);
        } catch (Throwable e) {
            log.error("Count execute failed", e);
            onError(dataItems, e);
        }
        return null;
    }

    /**
     * 抽象统计方法,由子类实现
     */
    protected abstract CountResult count(List<DataItem> dataItems);

    /**
     * 统计前钩子
     */
    protected void beforeCount(List<DataItem> dataItems) {

    }

    /**
     * 统计后钩子
     */
    protected CountResult afterCount(List<DataItem> dataItems, CountResult countResult) {
        return countResult;
    }

    /**
     * 错误处理钩子
     */
    protected void onError(List<DataItem> dataItems, Throwable e) {

    }

    @Override
    protected void doExecute() {
        long start = System.currentTimeMillis();
        WorkflowContext ctx = getWorkflowContext();
        List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
        if (CollectionUtils.isEmpty(dataItems)) {
            throw new EvalException("Data items is empty");
        }
        CountResult countResult = countWrapper(dataItems);
        if (countResult != null) {
            countResult.writeToCtx(ctx);
        }
        log.info("Counter execute success, time cost：{}ms", System.currentTimeMillis() - start);
    }
}
