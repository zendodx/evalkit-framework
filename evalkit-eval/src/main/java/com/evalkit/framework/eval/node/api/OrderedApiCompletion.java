package com.evalkit.framework.eval.node.api;

import com.evalkit.framework.common.thread.OrderedBatchRunner;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 有序API调用,适用于同组数据按顺序执行,例如:相同CaseId的Query要用同一线程处理,并且需要保证执行顺序
 */
public abstract class OrderedApiCompletion extends ApiCompletion {

    public OrderedApiCompletion() {
    }

    public OrderedApiCompletion(long timeout, TimeUnit timeUnit) {
        super(timeout, timeUnit);
    }

    public OrderedApiCompletion(int threadNum) {
        super(threadNum);
    }

    public OrderedApiCompletion(int threadNum, long timeout, TimeUnit timeUnit) {
        super(threadNum, timeout, timeUnit);
    }

    /**
     * 获取key,用于顺序执行
     *
     * @param dataItem 单条输入数据
     * @return 顺序执行key
     */
    public abstract String getOrderKey(DataItem dataItem);

    /**
     * 批量调用
     *
     * @param dataItems 输入数据集合
     * @return 调用结果集合
     */
    @Override
    protected List<ApiCompletionResult> batchInvoke(List<DataItem> dataItems) {
        return OrderedBatchRunner.runOrderedBatch(dataItems, this::invokeWrapper, this::getOrderKey, size -> size * SINGLE_TASK_TIMEOUT);
    }
}
