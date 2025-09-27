package com.evalkit.framework.eval.node.debuger;

import com.evalkit.framework.eval.constants.NodeNamePrefix;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.EvalResult;
import com.evalkit.framework.eval.node.scorer.strategy.ScoreStrategy;
import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.workflow.WorkflowContextHolder;
import com.evalkit.framework.workflow.model.WorkflowContext;
import com.evalkit.framework.workflow.model.WorkflowNode;
import com.evalkit.framework.workflow.utils.WorkflowUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 调试器,可注入工作流上下文数据,跳过某些节点的执行
 */
@Slf4j
public abstract class Debugger extends WorkflowNode {
    /* 是否包含评测结果 */
    protected boolean containsEvalResult = true;
    /* 分页 */
    protected int offset = 0;
    protected int limit = -1;

    public Debugger() {
    }

    public Debugger(int offset, int limit) {
        this.offset = offset;
        this.limit = limit;
    }

    public Debugger(boolean containsEvalResult) {
        this.containsEvalResult = containsEvalResult;
    }

    public Debugger(int offset, int limit, boolean containsEvalResult) {
        super(WorkflowUtils.generateNodeId(NodeNamePrefix.DEBUGGER));
        this.offset = offset;
        this.limit = limit;
        this.containsEvalResult = containsEvalResult;
    }

    protected abstract void inject();

    protected void afterInject() {
        WorkflowContext ctx = WorkflowContextHolder.get();
        List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
        if (offset > 0 && limit >= 0) {
            dataItems = dataItems.stream().skip(offset).limit(limit).collect(Collectors.toList());
        } else if (offset > 0) {
            dataItems = dataItems.stream().skip(offset).collect(Collectors.toList());
        } else if (limit >= 0) {
            dataItems = dataItems.stream().limit(limit).collect(Collectors.toList());
        }
        WorkflowContextOps.setDataItems(ctx, dataItems);
    }

    protected void injectWrapper() {
        try {
            inject();
            afterInject();
        } catch (Throwable e) {
            log.error("Inject ctx error", e);
            throw e;
        }
    }

    /**
     * 构造数据项列表
     */
    protected List<DataItem> buildDataItems(Map<String, Object> map, long dataIndex, ScoreStrategy strategy, double threshold) {
        List<DataItem> dataItems = new ArrayList<>();
        List<Map<String, Object>> t = (List<Map<String, Object>>) map.get("dataItems");
        for (Map<String, Object> m : t) {
            DataItem dataItem = buildDataItem(m, dataIndex, strategy, threshold);
            dataIndex++;
            dataItems.add(dataItem);
        }
        return dataItems;
    }

    /**
     * 构造单个数据项
     */
    protected DataItem buildDataItem(Map<String, Object> dataItemMap, long dataIndex, ScoreStrategy strategy, double threshold) {
        DataItem dataItem = MapUtils.fromMap(dataItemMap, DataItem.class);
        dataItem.setDataIndex(dataIndex);
        dataItem.getInputData().setDataIndex(dataIndex);
        dataItem.getApiCompletionResult().setDataIndex(dataIndex);
        if (!containsEvalResult) {
            EvalResult evalResult = new EvalResult();
            evalResult.setDataIndex(dataIndex);
            evalResult.setScorerResults(new ArrayList<>());
            evalResult.setScoreStrategyName(strategy.getStrategyName());
            evalResult.setScoreStrategy(strategy);
            evalResult.setThreshold(threshold);
            dataItem.setEvalResult(evalResult);
        }
        dataItem.getEvalResult().setDataIndex(dataIndex);
        return dataItem;
    }

    /**
     * 构造统计结果
     */
    protected Map<String, String> buildCountResultMap(Map<String, Object> map) {
        return (Map<String, String>) map.getOrDefault("countResult", null);
    }

    @Override
    protected void doExecute() {
        injectWrapper();
    }
}
