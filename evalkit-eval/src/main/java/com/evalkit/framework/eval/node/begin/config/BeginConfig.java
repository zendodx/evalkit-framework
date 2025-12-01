package com.evalkit.framework.eval.node.begin.config;

import com.evalkit.framework.eval.node.scorer.strategy.EvalReasonStrategy;
import com.evalkit.framework.eval.node.scorer.strategy.NormalEvalReasonStrategy;
import com.evalkit.framework.eval.node.scorer.strategy.ScoreStrategy;
import com.evalkit.framework.eval.node.scorer.strategy.SumScoreStrategy;
import lombok.Builder;
import lombok.Data;

/**
 * 开始节点配置, 可配置全局的参数
 */
@Data
@Builder
public class BeginConfig {
    /* 评测分数整合策略,默认求和策略 */
    @Builder.Default
    protected ScoreStrategy scoreStrategy = new SumScoreStrategy();
    /* 评测通过阈值,默认值0 */
    @Builder.Default
    protected double threshold = 0;
    /* 评估理由构建策略, 默认普通文本拼接构建策略 */
    @Builder.Default
    protected EvalReasonStrategy evalReasonStrategy = new NormalEvalReasonStrategy();
}
