package com.evalkit.framework.eval.node.scorer.strategy;

import com.evalkit.framework.eval.model.ScorerResult;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * 平均分数策略: 计算各评估器分数的平均值
 */
public class AvgScoreStrategy implements ScoreValueStrategy {
    @Override
    public String getStrategyName() {
        return "平均分数策略";
    }

    @Override
    public double calScore(List<ScorerResult> scorerResults) {
        if (CollectionUtils.isEmpty(scorerResults)) {
            return 0;
        }
        double lastScore = 0;
        int validCount = 0;
        for (ScorerResult scorerResult : scorerResults) {
            double curScore = scorerResult.getScore();
            if (curScore < 0) {
                continue;
            }
            lastScore += curScore;
            validCount++;
        }
        return validCount > 0 ? lastScore / validCount : 0;
    }
}
