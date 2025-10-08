package com.evalkit.framework.eval.node.api;

import com.evalkit.framework.common.thread.BatchRunner;
import com.evalkit.framework.common.thread.PoolName;
import com.evalkit.framework.eval.constants.NodeNamePrefix;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.workflow.model.WorkflowContext;
import com.evalkit.framework.workflow.model.WorkflowNode;
import com.evalkit.framework.workflow.utils.WorkflowUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 接口调用器
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public abstract class ApiCompletion extends WorkflowNode {
    /* 单任务超时时间 */
    protected final static long SINGLE_TASK_TIMEOUT = 60 * 10;
    /* 最大线程数 */
    protected final static int MAX_THREAD_NUM = Runtime.getRuntime().availableProcessors() * 2;
    /* 并发调用线程数 */
    protected int threadNum;
    /* 接口超时时间,默认120秒 */
    protected long timeout;
    protected TimeUnit timeUnit;

    public ApiCompletion() {
        this(1, 120, TimeUnit.SECONDS);
    }

    public ApiCompletion(long timeout, TimeUnit timeUnit) {
        this(1, timeout, timeUnit);
    }

    public ApiCompletion(int threadNum) {
        this(threadNum, 120, TimeUnit.SECONDS);
    }

    public ApiCompletion(int threadNum, long timeout, TimeUnit timeUnit) {
        super(WorkflowUtils.generateNodeId(NodeNamePrefix.API_COMPLETION));
        this.timeout = Math.max(1, timeout);
        this.timeUnit = timeUnit;
        this.threadNum = Math.min(threadNum, MAX_THREAD_NUM);
    }

    public void setTimeout(long timeout, TimeUnit timeUnit) {
        this.timeout = timeout;
        this.timeUnit = timeUnit;
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

    @Override
    public void doExecute() {
        long start = System.currentTimeMillis();
        WorkflowContext ctx = getWorkflowContext();
        List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
        if (CollectionUtils.isEmpty(dataItems)) {
            throw new EvalException("Data items is empty");
        }
        List<ApiCompletionResult> apiCompletionResults = BatchRunner.runBatch(dataItems, this::invokeWrapper, PoolName.API_COMPLETION, threadNum, size -> size * SINGLE_TASK_TIMEOUT);
        if (CollectionUtils.isEmpty(apiCompletionResults)) {
            throw new EvalException("Api completion result is empty");
        }
        dataItems.forEach(item -> apiCompletionResults.stream()
                .filter(r -> Objects.equals(r.getDataIndex(), item.getDataIndex()))
                .findFirst().ifPresent(item::setApiCompletionResult));
        log.info("Api completion success, time cost: {}ms", System.currentTimeMillis() - start);
    }
}