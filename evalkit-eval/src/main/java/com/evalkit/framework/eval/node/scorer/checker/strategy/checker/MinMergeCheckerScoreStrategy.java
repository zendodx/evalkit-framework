package com.evalkit.framework.eval.node.scorer.checker.strategy.checker;

import com.evalkit.framework.eval.node.scorer.checker.Checker;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * 检查器最小分数合并策略
 */
public class MinMergeCheckerScoreStrategy implements MergeCheckerScoreStrategy {
    @Override
    public String getStrategyName() {
        return "最小分数合并策略";
    }

    @Override
    public double mergeScore(List<Checker> checkers) {
        if (CollectionUtils.isEmpty(checkers)) {
            return 0;
        }
        double score = checkers.get(0).getScore();
        for (Checker checker : checkers) {
            score = Math.min(score, checker.getScore());
        }
        return score;
    }
}
