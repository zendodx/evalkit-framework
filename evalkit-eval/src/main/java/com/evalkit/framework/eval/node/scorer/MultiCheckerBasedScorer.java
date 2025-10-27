package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.scorer.checker.Checker;
import com.evalkit.framework.eval.node.scorer.checker.model.CheckItem;
import com.evalkit.framework.eval.node.scorer.checker.strategy.checker.MergeCheckerScoreStrategy;
import com.evalkit.framework.eval.node.scorer.checker.strategy.checker.SumMergeCheckerScoreStrategy;
import com.evalkit.framework.eval.node.scorer.config.ScorerConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于多检查器的评估器
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public abstract class MultiCheckerBasedScorer extends Scorer {
    /* 合并各检查器结果的策略,默认 求和策略 */
    protected MergeCheckerScoreStrategy strategy;

    public MultiCheckerBasedScorer(MergeCheckerScoreStrategy strategy) {
        this(ScorerConfig.builder().build(), strategy);
    }

    public MultiCheckerBasedScorer(ScorerConfig config) {
        this(config, new SumMergeCheckerScoreStrategy());
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
        // 动态计算评估器总分,等于所有检查器总分之和
        double score = mergeCheckerScore(checkers);
        String reason = mergeCheckerReason(checkers);
        // 额外信息存储各检查器的结果
        Map<String, Object> checkerResults = new HashMap<>();
        // 检查器总分数
        AtomicReference<Double> totalScore = new AtomicReference<>(0.0);
        checkers.forEach(checker -> {
            String key = checker.getCheckName();
            List<CheckItem> checkItems = checker.getCheckItems();
            checkerResults.put(key, JsonUtils.toJson(checkItems));
            // 累加检查器总分得到评估器分数
            totalScore.updateAndGet(v -> v + checker.getTotalScore());
        });
        ScorerResult scorerResult = new ScorerResult(config.getMetricName(), score, totalScore.get(), reason, null);
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
