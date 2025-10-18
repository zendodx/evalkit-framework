package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.scorer.checker.Checker;
import com.evalkit.framework.eval.node.scorer.checker.model.CheckItem;
import com.evalkit.framework.eval.node.scorer.checker.strategy.checker.MergeCheckerScoreStrategy;
import com.evalkit.framework.eval.node.scorer.checker.strategy.checker.SumMergeCheckerScoreStrategy;
import com.evalkit.framework.eval.node.scorer.config.ScorerConfig;
import com.evalkit.framework.common.utils.json.JsonUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于多检查器的评估器
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public abstract class MultiCheckerBasedScorer extends Scorer {
    /* 合并各检查器结果的策略,默认 求和策略 */
    protected MergeCheckerScoreStrategy strategy;

    public MultiCheckerBasedScorer(ScorerConfig config) {
        super(config);
        this.strategy = new SumMergeCheckerScoreStrategy();
    }

    public MultiCheckerBasedScorer(ScorerConfig config, MergeCheckerScoreStrategy strategy) {
        super(config);
        this.strategy = strategy;
    }

    /**
     * 准备检查器
     */
    public abstract List<Checker> prepareCheckers(DataItem dataItem);

    @Override
    public ScorerResult eval(DataItem dataItem) {
        // 准备,执行,汇总各检查器结果
        List<Checker> checkers = prepareCheckers(dataItem);
        checkers.forEach(checker -> checker.checkWrapper(dataItem));
        double score = mergeCheckerScore(checkers);
        String reason = mergeCheckerReason(checkers);
        // 额外信息存储各检查器的结果
        Map<String, Object> checkerResults = new HashMap<>();
        checkers.forEach(checker -> {
            String key = checker.getCheckName();
            List<CheckItem> checkItems = checker.getCheckItems();
            checkerResults.put(key, JsonUtils.toJson(checkItems));
        });
        ScorerResult scorerResult = new ScorerResult(config.getMetricName(), score, reason, null);
        scorerResult.addExtraItems(checkerResults);
        return scorerResult;
    }

    /**
     * 汇总各checker分数,汇总策略有:最小分数,平均分数
     */
    private double mergeCheckerScore(List<Checker> checkers) {
        return strategy.mergeScore(checkers);
    }

    /**
     * 汇总各checker不通过的检查项作为打分理由
     */
    private String mergeCheckerReason(List<Checker> checkers) {
        StringBuilder sb = new StringBuilder();
        for (Checker checker : checkers) {
            sb.append(String.format("[%s]:[%s]", checker.getCheckName(), checker.getReason()));
            sb.append("\n");
        }
        return sb.toString();
    }
}
