package com.evalkit.framework.eval.node.scorer.config;

import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.workflow.model.NodeConfig;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.function.Function;

/**
 * 评估器配置
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class ScorerConfig extends NodeConfig {
    /* 评估器名称 */
    @Builder.Default
    protected String metricName = "未命名指标";
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
    /* 场景路由条件，null=不过滤；不满足条件时生成跳过结果（score=skipScore，totalScore=0，不计入汇总） */
    @JsonIgnore
    @Builder.Default
    protected Function<DataItem, Boolean> condition = null;

    /* 条件不满足时跳过返回的默认分，默认 0，totalScore=0 不计入汇总 */
    @Builder.Default
    protected double skipScore = 0.0;
}
