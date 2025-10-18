package com.evalkit.framework.eval.node.scorer.checker.strategy.checker;

import com.evalkit.framework.eval.node.scorer.checker.Checker;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * 检查器总分数合并策略
 */
public class SumMergeCheckerScoreStrategy implements MergeCheckerScoreStrategy {
    @Override
    public String getStrategyName() {
        return "总分数合并策略";
    }

    @Override
    public double mergeScore(List<Checker> checkers) {
        if (CollectionUtils.isEmpty(checkers)) {
            return 0;
        }
        double score = 0;
        for (Checker checker : checkers) {
            score += checker.getScore();
        }
        return score;
    }
}
