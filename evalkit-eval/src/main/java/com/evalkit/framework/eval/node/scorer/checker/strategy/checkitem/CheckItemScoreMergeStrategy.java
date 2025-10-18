package com.evalkit.framework.eval.node.scorer.checker.strategy.checkitem;

import com.evalkit.framework.eval.node.scorer.checker.model.CheckItem;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 检查器的打分策略
 */
public interface CheckItemScoreMergeStrategy {
    double mergeScore(List<CheckItem> checkItems);

    /**
     * 检查必过项是否都通过,有些必过项如果没过则整体都是0分
     */
    default boolean isAllStarCheckItemPassed(List<CheckItem> checkItems) {
        List<CheckItem> items = checkItems.stream()
                .filter(item -> item.isStar() && item.getScore() == 0.0)
                .collect(Collectors.toList());
        return CollectionUtils.isEmpty(items);
    }
}
