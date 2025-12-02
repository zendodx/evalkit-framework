package com.evalkit.framework.eval.node.scorer.strategy;

import com.evalkit.framework.eval.model.ScorerResult;

import java.util.List;

/**
 * 普通文本格式的评测原因构建策略
 * <p>
 * 功能: 以文本格式返回整体理由, 将各评估器的评估理由拼接成一段话
 */
public class NormalEvalReasonStrategy implements EvalReasonStrategy {
    @Override
    public String buildEvalReason(List<ScorerResult> scorerResults) {
        return appendRawReason(scorerResults);
    }

    @Override
    public String getStrategyName() {
        return "普通文本评测原因构建策略";
    }
}
