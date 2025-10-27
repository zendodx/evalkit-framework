package com.evalkit.framework.eval.node.scorer.config;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * 评估器配置
 */
@Data
@SuperBuilder
public class ScorerConfig {
    /* 评估器名称 */
    @Builder.Default
    protected String metricName = "未命名指标";
    /* 评估线程数,默认值1 */
    @Builder.Default
    protected int threadNum = 1;
    /* 评估器通过阈值,默认值0 */
    @Builder.Default
    protected double threshold = 0.0;
    /* 是否为必过指标,默认false */
    @Builder.Default
    protected boolean star = false;
    /* 评估器总分数,默认1 */
    @Builder.Default
    protected double totalScore = 1;
    /* 动态评估器总分数,某些评估的总数分时运行中决定的,需要动态变化 */
    @Builder.Default
    protected boolean dynamicTotalScore = false;
}
