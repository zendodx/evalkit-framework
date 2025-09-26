package com.evalkit.framework.eval.context;

import com.evalkit.framework.eval.model.CountResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.node.scorer.strategy.ScoreStrategy;
import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.workflow.model.WorkflowContext;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 上下文操作门面
 */
public final class WorkflowContextOps {

    public static void setTaskName(WorkflowContext ctx, String taskName) {
        ctx.put(WorkflowContextKey.TASK_NAME, taskName);
    }

    public static String getTaskName(WorkflowContext ctx) {
        return ctx.get(WorkflowContextKey.TASK_NAME, String.class);
    }

    public static void setScorerStrategy(WorkflowContext ctx, ScoreStrategy strategy) {
        ctx.put(WorkflowContextKey.SCORE_STRATEGY, strategy);
    }

    public static ScoreStrategy getScorerStrategy(WorkflowContext ctx) {
        return ctx.get(WorkflowContextKey.SCORE_STRATEGY, ScoreStrategy.class);
    }

    public static void setThreshold(WorkflowContext ctx, double threshold) {
        ctx.put(WorkflowContextKey.THRESHOLD, threshold);
    }

    public static double getThreshold(WorkflowContext ctx) {
        Double v = ctx.get(WorkflowContextKey.THRESHOLD, Double.class);
        return v == null ? 0d : v;
    }

    public static void setDataItems(WorkflowContext ctx, List<DataItem> dataItems) {
        ctx.put(WorkflowContextKey.DATA_ITEM_LIST, dataItems);
    }

    public static List<DataItem> getDataItems(WorkflowContext ctx) {
        return ctx.get(WorkflowContextKey.DATA_ITEM_LIST, List.class);
    }

    public static void setCountResults(WorkflowContext ctx, Map<String, String> countResults) {
        ctx.put(WorkflowContextKey.COUNT_RESULT_MAP, countResults);
    }

    public static Map<String, String> getCountResults(WorkflowContext ctx) {
        return ctx.get(WorkflowContextKey.COUNT_RESULT_MAP, Map.class);
    }

    public static void setExtra(WorkflowContext ctx, Map<String, Object> extra) {
        ctx.put(WorkflowContextKey.EXTRA, extra);
    }

    public static Map<String, Object> getExtra(WorkflowContext ctx) {
        return ctx.get(WorkflowContextKey.EXTRA, Map.class);
    }

    public static void setCountResult(WorkflowContext ctx, CountResult result) {
        if (result == null) return;
        Map<String, Object> map = ctx.get(WorkflowContextKey.COUNT_RESULT_MAP, Map.class);
        map.put(result.counterName(), JsonUtils.toJson(result));
        ctx.put(WorkflowContextKey.COUNT_RESULT_MAP, map);
    }

    public static <T extends CountResult> T getCountResult(WorkflowContext ctx, String countName, Class<T> clazz) {
        Map<String, Object> countResultMap = ctx.get(WorkflowContextKey.COUNT_RESULT_MAP, Map.class);
        String countResultJson = (String) countResultMap.getOrDefault(countName, null);
        if (StringUtils.isEmpty(countResultJson)) return null;
        return JsonUtils.fromJson(countResultJson, clazz);
    }
}
