package com.evalkit.framework.eval.node.begin.config;

import com.evalkit.framework.eval.node.scorer.strategy.ScoreStrategy;
import com.evalkit.framework.eval.node.scorer.strategy.SumScoreStrategy;
import com.evalkit.framework.common.utils.time.DateUtils;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BeginConfig {
    // 评测分数整合策略,默认求和策略
    @Builder.Default
    protected ScoreStrategy scoreStrategy = new SumScoreStrategy();
    // 评测通过阈值,默认值0
    @Builder.Default
    protected double threshold = 0;
}
