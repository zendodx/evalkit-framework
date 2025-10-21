package com.evalkit.framework.eval.node.api;

import com.evalkit.framework.common.thread.BatchRunner;
import com.evalkit.framework.common.thread.PoolName;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.node.api.config.ApiCompletionConfig;
import com.evalkit.framework.workflow.model.WorkflowContext;
import com.evalkit.framework.workflow.model.WorkflowNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * 接口调用器
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public abstract class ApiCompletion extends WorkflowNode {
    /* api调用器配置 */
    protected ApiCompletionConfig config;

    public ApiCompletion() {
        config = ApiCompletionConfig.builder().build();
    }

    public ApiCompletion(ApiCompletionConfig config) {
        this.config = config;
    }

    /**
     * 调用前钩子
     */
    protected DataItem beforeInvoke(DataItem dataItem) {
        return dataItem;
    }

    /**
     * 调用
     */
    protected abstract ApiCompletionResult invoke(DataItem dataItem) throws IOException, InterruptedException;

    /**
     * 调用后钩子
     */
    protected ApiCompletionResult afterInvoke(DataItem dataItem, ApiCompletionResult result) {
        return result;
    }

    /**
     * 错误处理钩子
     */
    protected void onErrorInvoke(DataItem dataItem, Throwable e) {
    }

    /**
     * 包含钩子的调用,单数据项调用失败不能影响整体运行
     */
    protected ApiCompletionResult invokeWrapper(DataItem dataItem) {
        ApiCompletionResult result = new ApiCompletionResult(new LinkedHashMap<>());
        result.setDataIndex(dataItem.getDataIndex());
        try {
            long start = System.currentTimeMillis();
            dataItem = beforeInvoke(dataItem);
            ApiCompletionResult resultTmp = invoke(dataItem);
            long end = System.currentTimeMillis();
            if (resultTmp != null) {
                result.setResultItem(resultTmp.getResultItem());
                result.setStartTime(start);
                result.setEndTime(end);
                result.setTimeCost(end - start);
                result.setSuccess(true);
            }
        } catch (Throwable e) {
            log.error("Invoke api error", e);
            onErrorInvoke(dataItem, e);
            result.setSuccess(false);
        }
        return afterInvoke(dataItem, result);
    }

    protected List<ApiCompletionResult> batchInvoke(List<DataItem> dataItems) {
        return BatchRunner.runBatch(dataItems, this::invokeWrapper, PoolName.API_COMPLETION, config.getThreadNum(), size -> size * SINGLE_TASK_TIMEOUT);
    }

    @Override
    public void doExecute() {
        long start = System.currentTimeMillis();
        WorkflowContext ctx = getWorkflowContext();
        List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
        if (CollectionUtils.isEmpty(dataItems)) {
            throw new EvalException("Data items is empty");
        }
        List<ApiCompletionResult> apiCompletionResults = batchInvoke(dataItems);
        if (CollectionUtils.isEmpty(apiCompletionResults)) {
            throw new EvalException("Api completion result is empty");
        }
        dataItems.forEach(dataItem -> apiCompletionResults.stream()
                .filter(r -> Objects.equals(r.getDataIndex(), dataItem.getDataIndex()))
                .findFirst()
                .ifPresent(dataItem::setApiCompletionResult));
        log.info("Api completion success, time cost: {}ms", System.currentTimeMillis() - start);
    }
}