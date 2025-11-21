package com.evalkit.framework.eval.node.scorer.config;

import com.evalkit.framework.eval.node.scorer.checker.strategy.checker.MergeCheckerScoreStrategy;
import com.evalkit.framework.eval.node.scorer.checker.strategy.checker.SumMergeCheckerScoreStrategy;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class MultiCheckerBasedScorerConfig extends ScorerConfig {
    /* 合并各检查器结果的策略,默认 求和策略 */
    @Builder.Default
    protected MergeCheckerScoreStrategy strategy = new SumMergeCheckerScoreStrategy();
}
