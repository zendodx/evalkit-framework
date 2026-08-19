package com.evalkit.framework.eval.node.scorer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 由 LLM 动态生成的量规维度定义（JSON 反序列化用）。
 * <p>
 * 在动态 Rubric 场景下，动态量规评估器首先调用 LLM
 * 根据当前 query/task 自动生成一组评估维度，LLM 的回复被解析为此对象列表，
 * 随后框架再将它们转换为标准的 {@link RubricCriteria} 执行打分。
 * <p>
 * 字段映射关系（与 {@link RubricCriteria} 对应）：
 * <pre>
 * name          → RubricCriteria.name
 * definition    → RubricCriteria.definition
 * scoringGuide  → RubricCriteria.scoringGuide
 * maxScore      → RubricCriteria.maxScore（阶梯分时有效，默认 5）
 * weight        → RubricCriteria.weight（默认 1.0，等权重）
 * </pre>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneratedRubricCriteria {

    /**
     * 维度名称（英文标识，不超过 30 字符）。
     * 示例：{@code "IntentRecognition"}、{@code "ToolCallCorrectness"}
     */
    private String name;

    /**
     * 维度定义，描述该维度评估的目标是什么。
     * 示例：{@code "Whether the agent correctly identifies the user's core intent"}
     */
    private String definition;

    /**
     * 打分指引：对每个分段进行说明，直接注入评估 Prompt。
     * 示例：{@code "5=完全满足; 3=部分满足; 1=完全不满足"}
     */
    private String scoringGuide;

    /**
     * 最高分（阶梯分），默认 5。
     */
    private double maxScore = 5.0;

    /**
     * 最低分（阶梯分），默认 1。
     */
    private double minScore = 1.0;

    /**
     * 通过分数线（低于此分视为未达标），默认 3。
     */
    private double passScore = 3.0;

    /**
     * 维度权重（用于加权平均），默认 1.0。
     */
    private double weight = 1.0;
}

