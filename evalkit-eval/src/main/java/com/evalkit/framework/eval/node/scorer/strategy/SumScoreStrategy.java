package com.evalkit.framework.eval.node.scorer.strategy;


import com.evalkit.framework.eval.model.ScorerResult;

import java.util.List;

/**
 * 分数求和策略: 计算各评估器分数的和
 */
public class SumScoreStrategy implements ScoreValueStrategy {
    @Override
    public String getStrategyName() {
        return "分数求和策略";
    }

    @Override
    public double calScore(List<ScorerResult> scorerResults) {
        double sum = 0;
        for (ScorerResult scorerResult : scorerResults) {
            if (scorerResult.isSuccess()) {
                sum += scorerResult.getScore();
            }
        }
        return sum;
    }
}
