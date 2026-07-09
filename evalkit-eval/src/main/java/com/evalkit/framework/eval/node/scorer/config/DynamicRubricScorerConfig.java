package com.evalkit.framework.eval.node.scorer.config;

import com.evalkit.framework.eval.node.scorer.model.RubricCriteria;
import com.evalkit.framework.eval.node.scorer.model.RubricMergeStrategy;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 动态量规（Dynamic Rubric）评估器配置。
 * <p>
 * 在静态 {@link RubricBasedScorerConfig} 基础上扩展了以下能力：
 * <ul>
 *   <li><b>Rubric 生成 Prompt</b>：{@code rubricGenPrompt} 指导 LLM 如何根据当前 query/task 生成维度列表。</li>
 *   <li><b>静态兜底 Criteria</b>：{@code fallbackCriteria} 在 Rubric 生成失败时降级使用静态维度，
 *       避免评估中断。可为空（不配置兜底则直接抛异常）。</li>
 *   <li><b>维度数量限制</b>：{@code maxGeneratedCriteria} 限制 LLM 生成维度的上限，防止过多维度
 *       造成 Token 浪费，默认 8 个。</li>
 *   <li><b>混合模式</b>：{@code staticCriteria} 是每次都必须执行的静态公共维度（如安全检测），
 *       与动态生成的维度合并后一起评估。</li>
 * </ul>
 * <p>
 * 基本用法示例：
 * <pre>
 * DynamicRubricScorerConfig config = DynamicRubricScorerConfig.builder()
 *     .metricName("AgentQuality")
 *     .llmService(myLLMService)
 *     .rubricGenPrompt("根据以下用户请求，生成3-5个关键评估维度...")
 *     .mergeStrategy(RubricMergeStrategy.WEIGHTED_AVERAGE)
 *     .maxGeneratedCriteria(5)
 *     .build();
 * </pre>
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class DynamicRubricScorerConfig extends RubricBasedScorerConfig {

    /**
     * 默认的 Rubric 生成 Prompt。
     */
    public static final String DEFAULT_RUBRIC_GEN_PROMPT =
            "你是一位专业的 AI 评估专家。\n" +
            "请根据以下用户请求 / 任务描述，自动生成合适的评估维度，用于全面评估 AI 助手的回复质量。\n\n" +
            "要求：\n" +
            "1. 维度应紧贴当前任务的核心目标，不要包含泛泛的通用维度\n" +
            "2. 维度名称使用英文驼峰命名（如 IntentRecognition）\n" +
            "3. 评分范围统一为 1~5 分，其中 5 分=完全优秀，3 分=基本达标，1 分=严重不足\n" +
            "4. scoringGuide 必须包含 5、3、1 分的具体描述，便于 LLM 打分时对齐\n" +
            "5. passScore 设置为 3（即 60% 达标）\n" +
            "6. 只输出 JSON 数组，不要包含任何多余文字或 Markdown 代码块\n\n" +
            "7. 维度名称和维度定义使用中文描述\n\n" +
            "输出格式（严格遵守）：\n" +
            "[{\"name\":\"维度中文名称\",\"definition\":\"该维度评估什么\",\"scoringGuide\":\"5=...; 3=...; 1=...\"," +
            "\"maxScore\":5,\"minScore\":1,\"passScore\":3,\"weight\":1.0}]";

    /**
     * Rubric 生成阶段的 Prompt 模板。
     * <p>
     * 框架会将此 Prompt 与当前 query/task 文本拼接后发送给 LLM，
     * LLM 应返回 JSON 数组格式的维度列表。
     * <p>
     * 未配置时自动使用内置的 {@link #DEFAULT_RUBRIC_GEN_PROMPT}。
     */
    @Builder.Default
    private String rubricGenPrompt = DEFAULT_RUBRIC_GEN_PROMPT;

    /**
     * Rubric 生成失败时的静态兜底维度列表（可选）。
     * <p>
     * 当 LLM 生成 Rubric 失败（网络异常、格式解析错误等）时，
     * 框架将降级使用这些静态维度继续评估，保证评估流程不中断。
     * <p>
     * 若不配置兜底（{@code null}），则生成失败时直接抛出异常。
     */
    private List<RubricCriteria> fallbackCriteria;

    /**
     * LLM 生成维度的最大数量限制，默认 5。
     * <p>
     * 超过此数量的维度会被截断，防止 LLM 过度生成导致 Token 浪费和评估不聚焦。
     */
    @Builder.Default
    private int maxGeneratedCriteria = 5;

    /**
     * 每次评估都必须执行的静态公共维度列表（混合模式，可选）。
     * <p>
     * 这些维度与动态生成的维度合并后一起参与打分，适合配置对所有 query 均适用的
     * 公共评估标准（如安全性、无害性检测等）。
     * <p>
     * 合并后顺序：静态公共维度排在前面，动态生成维度追加在后。
     */
    private List<RubricCriteria> staticCriteria;

    /**
     * 维度分数综合策略（覆盖父类默认值），默认加权平均。
     * <p>
     * 由于动态 Rubric 的父类 criteria 字段在配置阶段为空（运行时动态生成），
     * 此字段用于在合并维度后传递给父类评估流程使用。
     */
    @Builder.Default
    private RubricMergeStrategy dynamicMergeStrategy = RubricMergeStrategy.WEIGHTED_AVERAGE;
}

