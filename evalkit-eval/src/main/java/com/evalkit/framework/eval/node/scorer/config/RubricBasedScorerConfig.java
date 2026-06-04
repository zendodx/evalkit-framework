package com.evalkit.framework.eval.node.scorer.config;

import com.evalkit.framework.eval.node.scorer.model.RubricCriteria;
import com.evalkit.framework.eval.node.scorer.model.RubricMergeStrategy;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 量规评估器配置
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class RubricBasedScorerConfig extends PromptBasedScorerConfig {

    /**
     * 量规维度列表（至少一个）
     * 每个维度对应一次独立的 LLM 评分调用
     */
    private List<RubricCriteria> criteria;

    /**
     * 维度分数综合策略，默认加权平均
     */
    @Builder.Default
    private RubricMergeStrategy mergeStrategy = RubricMergeStrategy.WEIGHTED_AVERAGE;

    /**
     * 每个维度的采样次数，默认 1 次（单次调用）
     * 设置 >1 时，框架会对同一维度发起多次 LLM 调用，取平均分以提升稳定性。
     * 建议在高精度评估场景设为 3，会相应增加 LLM 调用成本。
     */
    @Builder.Default
    private int samplingTimes = 1;

    /**
     * 运行Rubric指标的并发数, 默认1
     */
    @Builder.Default
    private int criteriaThreadNum = 1;

    /**
     * 每次 LLM 调用评估的维度数量，默认 1（每个维度独立调用）。
     * <p>
     * 设置 > 1 时，框架将多个维度合并进同一个 Prompt 一次性评估，可显著节省 Token 和调用次数。
     * <ul>
     *   <li>{@code 1}（默认）：每个维度独立调用，注意力最集中，打分最准确，推荐高精度场景使用。</li>
     *   <li>{@code 2~5}：适度批量，Token 成本与准确性之间的平衡点。</li>
     *   <li>{@code <= 0} 或超出实际维度数：自动降为实际维度总数（全部合并为一次调用）。</li>
     * </ul>
     * 注意：批量模式下，LLM 需同时输出多个维度的 JSON 对象数组，对模型指令跟随能力要求较高；
     * 若 LLM 回复格式不符，框架会对失败批次整体重试（不会单独重试批次内某个维度）。
     */
    @Builder.Default
    private int criteriaBatchSize = 1;

    /**
     * 是否对各维度原始分数归一化到 [0,1] 后再合并，默认 true（推荐开启）。
     * <p>
     * 开启时（默认）：各维度得分先通过 {@code (score - minScore) / (maxScore - minScore)} 映射到 [0,1]，
     * 再按权重合并，最终得分域为 [0,1]，{@code totalScore} 固定为 1.0。
     * 适用于维度量程不一致（如有的 1~5 分、有的 0/1 分）的场景，可避免量纲混乱。
     * <p>
     * 关闭时：直接使用 LLM 返回的原始分数合并，最终得分域取决于各维度的 {@code maxScore}，
     * {@code totalScore} 将动态计算为各维度加权 maxScore 之和（WEIGHTED_AVERAGE/SIMPLE_AVERAGE）
     * 或各维度 maxScore 的平均值，其他策略仍以归一化分数做 pass/fail 判断。
     * 适用于所有维度量程相同、希望结果可解读为原始分值的场景。
     */
    @Builder.Default
    private boolean normalizeScore = true;
}

