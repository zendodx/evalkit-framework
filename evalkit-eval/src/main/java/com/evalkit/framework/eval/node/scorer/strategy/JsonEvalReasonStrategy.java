package com.evalkit.framework.eval.node.scorer.strategy;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.eval.model.ScorerResult;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Json格式的评测原因构建策略
 * <p>
 * 功能: 以Json格式返回整体理由, 评估器名称作为key, 评估理由作为value
 */
public class JsonEvalReasonStrategy implements EvalReasonStrategy {
    @Override
    public String buildEvalReason(List<ScorerResult> scorerResults) {
        List<Map<String, String>> lastReason = new ArrayList<>();
        for (ScorerResult scorerResult : scorerResults) {
            String curReason = scorerResult.getReason();
            if (StringUtils.isEmpty(curReason)) {
                continue;
            }
            Map<String, String> scorerReasonMap = new LinkedHashMap<>();
            scorerReasonMap.put("Metric", scorerResult.getMetric());
            scorerReasonMap.put("Reason", curReason);
            lastReason.add(scorerReasonMap);
        }
        if (CollectionUtils.isEmpty(lastReason)) {
            return "";
        }
        return JsonUtils.toJson(lastReason);
    }

    @Override
    public String getStrategyName() {
        return "Json格式评测原因构建策略";
    }
}
