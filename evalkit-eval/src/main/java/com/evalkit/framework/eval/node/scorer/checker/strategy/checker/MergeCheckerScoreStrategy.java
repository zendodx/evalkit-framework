package com.evalkit.framework.eval.node.scorer.checker.strategy.checker;


import com.evalkit.framework.eval.node.scorer.checker.Checker;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

public interface MergeCheckerScoreStrategy {
    String getStrategyName();

    /**
     * 检查必过checker是否都通过,有些必过项如果没过则整体都是0分
     */
    default boolean isAllStarCheckerPassed(List<Checker> checkers) {
        List<Checker> items = checkers.stream()
                .filter(item -> item.isStar() && item.getScore() == 0.0)
                .collect(Collectors.toList());
        return CollectionUtils.isEmpty(items);
    }

    double mergeScore(List<Checker> checkers);
}
