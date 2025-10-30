package com.evalkit.framework.eval.node.scorer.strategy;

import com.evalkit.framework.eval.model.ScorerResult;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * 最小得分率策略
 */
public class MinScoreRateStrategy implements ScoreRateStrategy {
    @Override
    public String getStrategyName() {
        return "最小得分率策略";
    }

    /**
     * 计算最小得分率
     *
     * @param scorerResults 评估结果集
     * @return 最小得分率
     */
    @Override
    public double calScore(List<ScorerResult> scorerResults) {
        double minScoreRate = 0;
        for (ScorerResult scorerResult : scorerResults) {
            minScoreRate = Math.min(minScoreRate, scorerResult.getScoreRate());
        }
        return CollectionUtils.isNotEmpty(scorerResults) ? minScoreRate : 0;
    }
}
