package com.evalkit.framework.eval.node.debuger;


import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.node.scorer.strategy.ScoreStrategy;
import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.workflow.WorkflowContextHolder;
import com.evalkit.framework.workflow.model.WorkflowContext;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

/**
 * json字符串调试器
 */
public class JsonStringDebugger extends Debugger {
    /* 注入的数据 */
    protected String json;

    public JsonStringDebugger(String json) {
        this.json = json;
    }

    public JsonStringDebugger(boolean containsEvalResult, String json) {
        super(containsEvalResult);
        this.json = json;
    }

    protected void inject() {
        Map<String, Object> map = JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {
        });
        WorkflowContext ctx = WorkflowContextHolder.get();
        long dataIndex = 0;
        ScoreStrategy strategy = WorkflowContextOps.getScorerStrategy(ctx);
        double threshold = WorkflowContextOps.getThreshold(ctx);
        List<DataItem> dataItems = buildDataItems(map, dataIndex, strategy, threshold);
        WorkflowContextOps.setDataItems(ctx, dataItems);
        buildCountResultMap(map);
        WorkflowContextOps.setCountResults(ctx, buildCountResultMap(map));
        WorkflowContextHolder.set(ctx);
    }

    @Override
    protected void doExecute() {
        inject();
    }
}
