package com.evalkit.framework.eval.node.scorer.strategy;


import com.evalkit.framework.eval.model.ScorerResult;

import java.util.List;

/**
 * 最终评测分数的计算策略
 */
public interface ScoreStrategy {
    String getStrategyName();

    double calScore(List<ScorerResult> scorerResults);
}
