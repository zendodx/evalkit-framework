package com.evalkit.framework.eval.node.scorer.checker.strategy.checkitem;


import com.evalkit.framework.eval.node.scorer.checker.model.CheckItem;

import java.util.List;

/**
 * 平均分策略
 */
public class AverageCheckItemScoreMergeStrategy implements CheckItemScoreMergeStrategy {
    @Override
    public double mergeScore(List<CheckItem> checkItems) {
        if (!isAllStarCheckItemPassed(checkItems)) {
            return 0.0;
        }
        return checkItems.stream()
                .filter(item -> item.getScore() >= 0)
                .mapToDouble(CheckItem::getWeightScore).average().orElse(0);
    }
}
