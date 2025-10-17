package com.evalkit.framework.eval.node.scorer.strategy;

import com.evalkit.framework.eval.model.ScorerResult;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * 最小分数策略: 取各评估器的最小分数
 */
public class MinScoreStrategy implements ScoreStrategy {
    @Override
    public String getStrategyName() {
        return "最小分数策略";
    }

    @Override
    public double calScore(List<ScorerResult> scorerResults) {
        if (CollectionUtils.isEmpty(scorerResults)) {
            return 0;
        }
        double lastScore = scorerResults.get(0).getScore();
        for (ScorerResult scorerResult : scorerResults) {
            double curScore = scorerResult.getScore();
            if (curScore < 0) {
                continue;
            }
            lastScore = Math.min(lastScore, curScore);
        }
        return lastScore;
    }
}
