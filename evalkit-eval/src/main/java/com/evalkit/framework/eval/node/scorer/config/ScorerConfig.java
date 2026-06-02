package com.evalkit.framework.eval.node.scorer.config;

import com.evalkit.framework.eval.model.DataItem;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.function.Function;

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
    /**
     * 场景路由条件
     * <p>当 {@code condition} 不为 {@code null} 时，只有满足条件的 {@link DataItem} 才会被本 Scorer 评估；
     * 不满足条件的 DataItem 将自动生成一条"已跳过"结果（score = skipScore，totalScore = 0，不计入汇总）。
     * {@code null} 表示不过滤，对所有 DataItem 均执行评估（默认行为，向前兼容）。</p>
     *
     * <pre>{@code
     * ScorerConfig.builder()
     *     .metricName("对话质量")
     *     .condition(item -> "chat".equals(item.getInputData().get("scene")))
     *     .build();
     * }</pre>
     */
    @JsonIgnore
    @Builder.Default
    protected Function<DataItem, Boolean> condition = null;

    /**
     * 条件不满足时跳过返回的默认分，默认 0。
     * <p>该结果的 totalScore = 0，因此不会影响汇总分数的计算。</p>
     */
    @Builder.Default
    protected double skipScore = 0.0;
}
