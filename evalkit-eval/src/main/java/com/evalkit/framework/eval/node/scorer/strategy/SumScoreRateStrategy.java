package com.evalkit.framework.eval.node.scorer.strategy;

import com.evalkit.framework.eval.model.ScorerResult;

import java.util.List;

/**
 * 得分率求和策略
 */
public class SumScoreRateStrategy implements ScoreRateStrategy {
    @Override
    public String getStrategyName() {
        return "得分率求和策略";
    }

    @Override
    public double calScore(List<ScorerResult> scorerResults) {
        double sumScoreRate = 0;
        for (ScorerResult scorerResult : scorerResults) {
            sumScoreRate += scorerResult.getScoreRate();
        }
        return sumScoreRate;
    }
}