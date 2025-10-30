package com.evalkit.framework.eval.node.scorer.strategy;

import com.evalkit.framework.eval.model.ScorerResult;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * 平均得分率策略
 */
public class AvgScoreRateStrategy implements ScoreRateStrategy {

    @Override
    public String getStrategyName() {
        return "平均得分率策略";
    }

    /**
     * 计算每个评估器的得分率后取平均值
     *
     * @param scorerResults 评估器结果集
     * @return 整体平均得分率
     */
    @Override
    public double calScore(List<ScorerResult> scorerResults) {
        double avgScoreRate = 0;
        for (ScorerResult scorerResult : scorerResults) {
            avgScoreRate += scorerResult.getScoreRate();
        }
        return CollectionUtils.isNotEmpty(scorerResults) ? avgScoreRate / scorerResults.size() : 0;
    }
}
