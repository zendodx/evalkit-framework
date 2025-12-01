package com.evalkit.framework.eval.node.scorer.strategy;

import com.evalkit.framework.eval.model.ScorerResult;

import java.util.List;

/**
 * 评测原因构建策略
 */
public interface EvalReasonStrategy {
    /**
     * 构建评测原因
     *
     * @param scorerResults 评估器结果集合
     * @return 评测原因
     */
    String buildEvalReason(List<ScorerResult> scorerResults);

    /**
     * 获取构建评测原因策略的名称
     *
     * @return 策略名称
     */
    String getStrategyName();
}
