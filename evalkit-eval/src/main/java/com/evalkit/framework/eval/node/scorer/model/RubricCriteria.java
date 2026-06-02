package com.evalkit.framework.eval.node.scorer.model;

import com.evalkit.framework.eval.model.DataItem;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.function.Function;

/**
 * 量规评估维度定义
 * <p>
 * 一个 RubricCriteria 描述一个独立的评估维度，包括其名称、定义、打分规则和 Few-shot 锚点示例。
 * 每个维度会对应一次独立的 LLM 调用，以保证评分准确性。
 */
@Data
@Builder
public class RubricCriteria {

    /**
     * 维度名称（英文，作为 JSON Key 和展示标识）
     * 示例: "Faithfulness", "Harmfulness"
     */
    private String name;

    /**
     * 维度定义，描述该维度评估的目标是什么
     * 示例: "输出是否忠实于提供的上下文/源材料，不包含任何捏造或无中生有的内容"
     */
    private String definition;

    /**
     * 评分类型：阶梯分 or 二元分
     */
    @Builder.Default
    private RubricScoreType scoreType = RubricScoreType.BINARY;

    /**
     * 最高分（阶梯分时有效）
     * 示例: 5
     */
    @Builder.Default
    private double maxScore = 1.0;

    /**
     * 最低分（阶梯分时有效）
     */
    @Builder.Default
    private double minScore = 0.0;

    /**
     * 通过分数线：归一化后低于此比率（passScore/maxScore）视为该维度未达标
     * 示例: passScore=3, maxScore=5 → passRate=0.6
     */
    @Builder.Default
    private double passScore = 1.0;

    /**
     * 打分指引：对每个分段进行明确说明，直接注入 Prompt
     * 示例（阶梯分）: "5分=完全忠实无捏造; 4分=基本忠实偶有小偏差; 3分=整体可信但有部分遗漏; 2分=有明显捏造或错误; 1分=大量虚构内容"
     * 示例（二元分）: "1=无有害内容; 0=包含有害内容"
     */
    private String scoringGuide;

    /**
     * Few-shot 锚点示例列表（强烈建议配置，可显著提升打分一致性）
     * 每个锚点描述"这个分值对应什么样的输出"，帮助 LLM 校准分数中间值。
     */
    private List<ScoringAnchor> anchors;

    /**
     * 维度权重，用于 WEIGHTED_AVERAGE 策略
     * 默认 1.0（等权重）
     */
    @Builder.Default
    private double weight = 1.0;

    /**
     * 是否为必过维度（一票否决）
     * 当 mergeStrategy=STAR_GATE 时，star=true 的维度归一化分为 0 会导致整体得分为 0
     */
    @Builder.Default
    private boolean star = false;

    /**
     * 条件执行函数（可选）：接收当前 DataItem，返回 true 表示执行该维度评估，false 表示跳过。
     * <p>
     * 默认为 null，表示始终执行。典型用法：
     * <pre>
     * RubricCriteria.builder()
     *     .name("ContextRelevance")
     *     .condition(item -&gt; item.getInputData().get("context") != null)  // 有上下文才评估
     *     .skipScore(3.0)  // 跳过时默认得 3 分（中性分）
     *     .build()
     * </pre>
     */
    private Function<DataItem, Boolean> condition;

    /**
     * 跳过时的默认原始分数，默认 0.0。
     * 仅在 condition 返回 false（不执行该维度）时生效。
     * 建议根据业务语义设置：
     * <ul>
     *   <li>0.0：保守策略，视为未通过</li>
     *   <li>minScore：最低分（同 0 效果，但语义更清晰）</li>
     *   <li>maxScore：豁免策略，视为满分通过</li>
     *   <li>passScore：中性策略，视为刚好通过</li>
     * </ul>
     */
    @Builder.Default
    private double skipScore = 0.0;

    // ==================== 内部类 ====================

    /**
     * Few-shot 打分锚点：对某个具体分值的文字说明或示例
     */
    @Data
    @Builder
    public static class ScoringAnchor {
        /**
         * 该锚点对应的分值
         */
        private double score;
        /**
         * 对该分值的文字描述或示例（会注入到 Prompt 中）
         */
        private String description;
    }

    // ==================== 工具方法 ====================

    /**
     * 判断当前 DataItem 是否应执行该维度评估。
     * condition 为 null 时始终返回 true；condition 返回 null 时同样视为 true（防御性处理）。
     */
    public boolean shouldEval(DataItem dataItem) {
        if (condition == null) {
            return true;
        }
        Boolean result = condition.apply(dataItem);
        return result == null || result;
    }

    /**
     * 计算该维度的通过率阈值（用于归一化比较）
     */
    public double getPassRate() {
        return maxScore > 0 ? passScore / maxScore : 0;
    }

    /**
     * 将原始分归一化到 [0, 1]
     * fix#4: 当 minScore > 0 时（如 1~5 分量规），使用区间归一化 (score - minScore) / (maxScore - minScore)，
     * 而非直接除以 maxScore，否则分数永远不可能到 0，导致 STAR_GATE / LOGICAL_AND 的零值判断失效。
     */
    public double normalize(double rawScore) {
        double range = maxScore - minScore;
        if (range <= 0) return 0;
        double clamped = Math.min(Math.max(rawScore, minScore), maxScore);
        return (clamped - minScore) / range;
    }
}

