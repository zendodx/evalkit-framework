package com.evalkit.framework.eval.node.scorer.strategy;


import com.evalkit.framework.eval.model.ScorerResult;

import java.util.List;

/**
 * 最终评测分数的计算策略
 */
public interface ScoreStrategy {
    /**
     * 获取策略名称
     *
     * @return 策略名称
     */
    String getStrategyName();

    /**
     * 计算评测分数
     *
     * @param scorerResults 评测结果集
     * @return 最终分数
     */
    double calScore(List<ScorerResult> scorerResults);
}
