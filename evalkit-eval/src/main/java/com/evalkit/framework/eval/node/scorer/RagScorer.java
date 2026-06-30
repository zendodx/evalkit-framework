package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.common.thread.BatchRunner;
import com.evalkit.framework.common.thread.PoolName;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.scorer.config.PromptBasedScorerConfig;
import com.evalkit.framework.eval.node.scorer.config.RagScorerConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 综合评估器（RAG Scorer）。
 *
 * <p>将 RAG 评测三件套——忠实度（Faithfulness）、上下文召回率（Context Recall）、上下文精度（Context Precision）
 * 打包为一个开箱即用的单节点评估器，无需分别配置三个 Scorer 节点。
 *
 * <p><b>内部工作机制</b>：
 * <ul>
 *   <li>三个子评测器并行发起 LLM 调用（使用 {@code SCORER_CRITERIA} 线程池，与主 Scorer 池隔离）</li>
 *   <li>最终分数 = Faithfulness × faithfulnessWeight + ContextRecall × contextRecallWeight
 *       + ContextPrecision × contextPrecisionWeight（加权平均后归一化到 0~1）</li>
 *   <li>三个子指标的分数和原因均写入 {@code ScorerResult.extra}，便于报告展示和细粒度分析</li>
 * </ul>
 *
 * <p><b>extra 字段说明</b>（可通过 {@code scorerResult.getExtraItem(key)} 获取）：
 * <ul>
 *   <li>{@link #EXTRA_KEY_FAITHFULNESS_SCORE} - 忠实度原始分</li>
 *   <li>{@link #EXTRA_KEY_FAITHFULNESS_REASON} - 忠实度评测理由</li>
 *   <li>{@link #EXTRA_KEY_CONTEXT_RECALL_SCORE} - 上下文召回率原始分</li>
 *   <li>{@link #EXTRA_KEY_CONTEXT_RECALL_REASON} - 上下文召回率评测理由</li>
 *   <li>{@link #EXTRA_KEY_CONTEXT_PRECISION_SCORE} - 上下文精度原始分</li>
 *   <li>{@link #EXTRA_KEY_CONTEXT_PRECISION_REASON} - 上下文精度评测理由</li>
 * </ul>
 *
 * <p><b>使用方式</b>（子类实现四个数据准备方法）：
 * <pre>
 * // 1. 全三项评测（默认，等权重）
 * RagScorer ragScorer = new RagScorer(
 *     RagScorerConfig.builder()
 *         .metricName("RAG综合质量")
 *         .llmService(llmService)
 *         .threshold(0.6)
 *         .build()
 * ) {
 *     {@literal @}Override
 *     public String prepareQuery(InputData in, ApiCompletionResult out) {
 *         return in.get("query");
 *     }
 *     {@literal @}Override
 *     public String prepareGroundTruth(InputData in, ApiCompletionResult out) {
 *         return in.get("groundTruth");
 *     }
 *     {@literal @}Override
 *     public String prepareContext(InputData in, ApiCompletionResult out) {
 *         return in.get("context");
 *     }
 *     {@literal @}Override
 *     public String prepareAnswer(InputData in, ApiCompletionResult out) {
 *         return out.get("response");
 *     }
 * };
 *
 * // 2. 仅评测 Faithfulness + ContextRecall（关闭 ContextPrecision）
 * RagScorer ragScorer = new RagScorer(
 *     RagScorerConfig.builder()
 *         .metricName("RAG质量")
 *         .llmService(llmService)
 *         .enableContextPrecision(false)  // 关闭精度评测
 *         .faithfulnessWeight(0.6)        // 自定义权重
 *         .contextRecallWeight(0.4)
 *         .build()
 * ) { ... };
 * </pre>
 *
 * @see FaithfulnessScorer
 * @see ContextRecallScorer
 * @see ContextPrecisionScorer
 */
@Slf4j
public abstract class RagScorer extends Scorer {

    // ===================== extra key 常量 =====================

    /** 忠实度分数 key */
    public static final String EXTRA_KEY_FAITHFULNESS_SCORE = "rag_faithfulness_score";
    /** 忠实度评测理由 key */
    public static final String EXTRA_KEY_FAITHFULNESS_REASON = "rag_faithfulness_reason";
    /** 上下文召回率分数 key */
    public static final String EXTRA_KEY_CONTEXT_RECALL_SCORE = "rag_context_recall_score";
    /** 上下文召回率评测理由 key */
    public static final String EXTRA_KEY_CONTEXT_RECALL_REASON = "rag_context_recall_reason";
    /** 上下文精度分数 key */
    public static final String EXTRA_KEY_CONTEXT_PRECISION_SCORE = "rag_context_precision_score";
    /** 上下文精度评测理由 key */
    public static final String EXTRA_KEY_CONTEXT_PRECISION_REASON = "rag_context_precision_reason";

    protected final RagScorerConfig ragConfig;

    // ===================== 内部子评测器（懒初始化） =====================

    private final FaithfulnessScorer faithfulnessScorer;
    private final ContextRecallScorer contextRecallScorer;
    private final ContextPrecisionScorer contextPrecisionScorer;

    public RagScorer(RagScorerConfig config) {
        super(config);
        this.ragConfig = config;
        super.scorerType = "ragScorer";

        // 构建内部子评测器——共享同一个 LLMService，各自独立配置
        PromptBasedScorerConfig faithfulnessConfig = buildSubConfig(
                config.getMetricName() + "_Faithfulness",
                config, config.getFaithfulnessSysPrompt());
        PromptBasedScorerConfig recallConfig = buildSubConfig(
                config.getMetricName() + "_ContextRecall",
                config, config.getContextRecallSysPrompt());
        PromptBasedScorerConfig precisionConfig = buildSubConfig(
                config.getMetricName() + "_ContextPrecision",
                config, config.getContextPrecisionSysPrompt());

        // 子评测器委托给 this 获取数据
        this.faithfulnessScorer = new FaithfulnessScorer(faithfulnessConfig) {
            @Override
            public String prepareQuery(InputData in, ApiCompletionResult out) {
                return RagScorer.this.prepareQuery(in, out);
            }
            @Override
            public String prepareContext(InputData in, ApiCompletionResult out) {
                return RagScorer.this.prepareContext(in, out);
            }
            @Override
            public String prepareAnswer(InputData in, ApiCompletionResult out) {
                return RagScorer.this.prepareAnswer(in, out);
            }
        };

        this.contextRecallScorer = new ContextRecallScorer(recallConfig) {
            @Override
            public String prepareQuery(InputData in, ApiCompletionResult out) {
                return RagScorer.this.prepareQuery(in, out);
            }
            @Override
            public String prepareGroundTruth(InputData in, ApiCompletionResult out) {
                return RagScorer.this.prepareGroundTruth(in, out);
            }
            @Override
            public String prepareContext(InputData in, ApiCompletionResult out) {
                return RagScorer.this.prepareContext(in, out);
            }
        };

        this.contextPrecisionScorer = new ContextPrecisionScorer(precisionConfig) {
            @Override
            public String prepareQuery(InputData in, ApiCompletionResult out) {
                return RagScorer.this.prepareQuery(in, out);
            }
            @Override
            public String prepareGroundTruth(InputData in, ApiCompletionResult out) {
                return RagScorer.this.prepareGroundTruth(in, out);
            }
            @Override
            public String prepareContext(InputData in, ApiCompletionResult out) {
                return RagScorer.this.prepareContext(in, out);
            }
        };
    }

    // ===================== 数据准备方法（子类实现） =====================

    /**
     * 准备用户问题
     *
     * @param inputData           输入数据
     * @param apiCompletionResult API 调用结果
     * @return 用户原始问题文本
     */
    public abstract String prepareQuery(InputData inputData, ApiCompletionResult apiCompletionResult);

    /**
     * 准备标准答案（Ground Truth），用于 ContextRecall 和 ContextPrecision 评测
     *
     * @param inputData           输入数据
     * @param apiCompletionResult API 调用结果
     * @return 人工标注的标准答案文本
     */
    public abstract String prepareGroundTruth(InputData inputData, ApiCompletionResult apiCompletionResult);

    /**
     * 准备检索上下文（RAG 检索到的文档片段，按检索排名顺序）
     *
     * @param inputData           输入数据
     * @param apiCompletionResult API 调用结果
     * @return 检索到的上下文文本，多段内容建议用换行分隔
     */
    public abstract String prepareContext(InputData inputData, ApiCompletionResult apiCompletionResult);

    /**
     * 准备模型答案（被评测的 RAG 系统输出），用于 Faithfulness 评测
     *
     * @param inputData           输入数据
     * @param apiCompletionResult API 调用结果
     * @return 模型生成的答案文本
     */
    public abstract String prepareAnswer(InputData inputData, ApiCompletionResult apiCompletionResult);

    // ===================== 核心评测逻辑 =====================

    @Override
    public ScorerResult eval(DataItem dataItem) throws Exception {
        boolean enableFaithfulness = ragConfig.isEnableFaithfulness();
        boolean enableContextRecall = ragConfig.isEnableContextRecall();
        boolean enableContextPrecision = ragConfig.isEnableContextPrecision();

        // 构建并行任务列表（Supplier 无受检异常，内部包装受检异常为运行时异常）
        List<SubTask> subTasks = new java.util.ArrayList<>();
        if (enableFaithfulness) {
            subTasks.add(new SubTask("faithfulness", () -> {
                try {
                    ScorerResult r = faithfulnessScorer.eval(dataItem);
                    return new SubResult("faithfulness", r.getScore(), r.getReason());
                } catch (Exception e) {
                    throw new RuntimeException("FaithfulnessScorer eval failed", e);
                }
            }));
        }
        if (enableContextRecall) {
            subTasks.add(new SubTask("contextRecall", () -> {
                try {
                    ScorerResult r = contextRecallScorer.eval(dataItem);
                    return new SubResult("contextRecall", r.getScore(), r.getReason());
                } catch (Exception e) {
                    throw new RuntimeException("ContextRecallScorer eval failed", e);
                }
            }));
        }
        if (enableContextPrecision) {
            subTasks.add(new SubTask("contextPrecision", () -> {
                try {
                    ScorerResult r = contextPrecisionScorer.eval(dataItem);
                    return new SubResult("contextPrecision", r.getScore(), r.getReason());
                } catch (Exception e) {
                    throw new RuntimeException("ContextPrecisionScorer eval failed", e);
                }
            }));
        }

        if (subTasks.isEmpty()) {
            throw new IllegalStateException("RagScorer: 至少需要启用一项 RAG 评测指标（Faithfulness/ContextRecall/ContextPrecision）");
        }

        // 并行执行——使用 SCORER_CRITERIA 池，与外层 SCORER 池隔离，防止死锁
        List<SubResult> subResults = BatchRunner.runBatch(
                subTasks, SubTask::execute, PoolName.SCORER_CRITERIA,
                subTasks.size(), size -> size * ragConfig.getSubScorerTimeoutSec());

        // 聚合分数和理由
        double totalWeight = 0.0;
        double weightedScore = 0.0;
        Map<String, Object> extra = new HashMap<>();
        StringBuilder reasonBuilder = new StringBuilder();

        for (SubResult sub : subResults) {
            double weight;
            switch (sub.type) {
                case "faithfulness":
                    weight = ragConfig.getFaithfulnessWeight();
                    extra.put(EXTRA_KEY_FAITHFULNESS_SCORE, sub.score);
                    extra.put(EXTRA_KEY_FAITHFULNESS_REASON, sub.reason);
                    reasonBuilder.append(String.format("[忠实度=%.2f] %s  ", sub.score, sub.reason));
                    break;
                case "contextRecall":
                    weight = ragConfig.getContextRecallWeight();
                    extra.put(EXTRA_KEY_CONTEXT_RECALL_SCORE, sub.score);
                    extra.put(EXTRA_KEY_CONTEXT_RECALL_REASON, sub.reason);
                    reasonBuilder.append(String.format("[上下文召回率=%.2f] %s  ", sub.score, sub.reason));
                    break;
                case "contextPrecision":
                    weight = ragConfig.getContextPrecisionWeight();
                    extra.put(EXTRA_KEY_CONTEXT_PRECISION_SCORE, sub.score);
                    extra.put(EXTRA_KEY_CONTEXT_PRECISION_REASON, sub.reason);
                    reasonBuilder.append(String.format("[上下文精度=%.2f] %s  ", sub.score, sub.reason));
                    break;
                default:
                    weight = 1.0;
            }
            totalWeight += weight;
            weightedScore += sub.score * weight;
        }

        double finalScore = totalWeight > 0 ? weightedScore / totalWeight : 0.0;

        ScorerResult result = new ScorerResult();
        result.setMetric(ragConfig.getMetricName());
        result.setScore(finalScore);
        result.setReason(reasonBuilder.toString().trim());
        result.setExtra(extra);
        return result;
    }

    // ===================== 私有工具方法 =====================

    private static PromptBasedScorerConfig buildSubConfig(String metricName, RagScorerConfig parent, String customSysPrompt) {
        PromptBasedScorerConfig.PromptBasedScorerConfigBuilder<?, ?> builder = PromptBasedScorerConfig.builder()
                .metricName(metricName)
                .llmService(parent.getLlmService())
                .enableRetry(parent.isEnableRetry())
                .retryTimes(parent.getRetryTimes())
                .retryInterval(parent.getRetryInterval())
                .retryTimeUnit(parent.getRetryTimeUnit())
                .threshold(0.0)
                .totalScore(1.0);
        if (StringUtils.isNotEmpty(customSysPrompt)) {
            builder.sysPrompt(customSysPrompt);
        }
        return builder.build();
    }

    /** 子评测器执行结果临时对象 */
    private static class SubResult {
        final String type;
        final double score;
        final String reason;

        SubResult(String type, double score, String reason) {
            this.type = type;
            this.score = score;
            this.reason = reason == null ? "" : reason;
        }
    }

    /**
     * 子任务包装器：封装任务名称和具体执行逻辑（使用 Supplier 避免受检异常问题）。
     * BatchRunner 接收 {@code Function<SubTask, SubResult>}，通过 {@link #execute()} 触发实际评测。
     */
    private static class SubTask {
        final String name;
        private final java.util.function.Supplier<SubResult> supplier;

        SubTask(String name, java.util.function.Supplier<SubResult> supplier) {
            this.name = name;
            this.supplier = supplier;
        }

        SubResult execute() {
            return supplier.get();
        }
    }
}

