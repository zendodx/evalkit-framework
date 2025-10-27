package com.evalkit.framework.eval.node.scorer.strategy;

import com.evalkit.framework.eval.model.ScorerResult;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * 最大得分率策略
 */
public class MaxScoreRateStrategy implements ScoreStrategy {
    @Override
    public String getStrategyName() {
        return "最大得分率策略";
    }

    /**
     * 计算最大得分率
     *
     * @param scorerResults 评估结果集
     * @return 最大得分率
     */
    @Override
    public double calScore(List<ScorerResult> scorerResults) {
        double maxScoreRate = 0;
        for (ScorerResult scorerResult : scorerResults) {
            maxScoreRate = Math.max(maxScoreRate, scorerResult.getScoreRate());
        }
        return CollectionUtils.isNotEmpty(scorerResults) ? maxScoreRate : 0;
    }
}
