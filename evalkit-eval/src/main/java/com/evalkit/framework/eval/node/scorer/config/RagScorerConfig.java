package com.evalkit.framework.eval.node.scorer.config;

import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.concurrent.TimeUnit;

/**
 * RAG 综合评估器配置（{@link com.evalkit.framework.eval.node.scorer.RagScorer}）。
 *
 * <p>支持按需启用/关闭三个子指标，并为每个子指标配置独立权重。
 *
 * <p><b>默认行为</b>：三个指标全部启用，等权重（各 1.0），
 * 最终分数 = (Faithfulness + ContextRecall + ContextPrecision) / 3。
 *
 * <p><b>示例</b>（仅评测 Faithfulness + ContextRecall，关闭 ContextPrecision）：
 * <pre>
 * RagScorerConfig config = RagScorerConfig.builder()
 *     .metricName("RAG质量")
 *     .llmService(llmService)
 *     .threshold(0.6)
 *     .enableContextPrecision(false)
 *     .faithfulnessWeight(0.6)
 *     .contextRecallWeight(0.4)
 *     .build();
 * </pre>
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class RagScorerConfig extends ScorerConfig {

    // ===================== LLM 服务配置 =====================

    /** 用于驱动三个子评测器的 LLM 服务（必填） */
    protected LLMService llmService;

    // ===================== 子指标开关 =====================

    /** 是否启用忠实度（Faithfulness）评测，默认 true */
    @Builder.Default
    protected boolean enableFaithfulness = true;

    /** 是否启用上下文召回率（Context Recall）评测，默认 true */
    @Builder.Default
    protected boolean enableContextRecall = true;

    /** 是否启用上下文精度（Context Precision）评测，默认 true */
    @Builder.Default
    protected boolean enableContextPrecision = true;

    // ===================== 子指标权重 =====================

    /**
     * 忠实度权重，默认 1.0。
     * 最终分数 = 各启用指标的加权平均值（自动归一化）。
     */
    @Builder.Default
    protected double faithfulnessWeight = 1.0;

    /** 上下文召回率权重，默认 1.0 */
    @Builder.Default
    protected double contextRecallWeight = 1.0;

    /** 上下文精度权重，默认 1.0 */
    @Builder.Default
    protected double contextPrecisionWeight = 1.0;

    // ===================== LLM 调用配置 =====================

    /** 是否开启子评测器 LLM 调用失败重试，默认 true */
    @Builder.Default
    protected boolean enableRetry = true;

    /** 重试间隔，默认 10 秒 */
    @Builder.Default
    protected long retryInterval = 10;

    /** 重试时间单位，默认秒 */
    @Builder.Default
    protected TimeUnit retryTimeUnit = TimeUnit.SECONDS;

    /** 最大重试次数，默认 6 次 */
    @Builder.Default
    protected int retryTimes = 6;

    // ===================== 自定义 Prompt（可选） =====================

    /**
     * 自定义忠实度评测 Prompt，为 null 时使用 FaithfulnessScorer 内置 Prompt。
     */
    protected String faithfulnessSysPrompt;

    /**
     * 自定义上下文召回率评测 Prompt，为 null 时使用 ContextRecallScorer 内置 Prompt。
     */
    protected String contextRecallSysPrompt;

    /**
     * 自定义上下文精度评测 Prompt，为 null 时使用 ContextPrecisionScorer 内置 Prompt。
     */
    protected String contextPrecisionSysPrompt;

    // ===================== 执行配置 =====================

    /**
     * 单个子评测器的超时时间（秒/条数据），默认 120 秒。
     * 传递给 BatchRunner：总超时 = subScorerTimeoutSec × 数据条数。
     */
    @Builder.Default
    protected long subScorerTimeoutSec = 120;
}

