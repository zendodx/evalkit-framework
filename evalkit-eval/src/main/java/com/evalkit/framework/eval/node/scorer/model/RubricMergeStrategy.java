package com.evalkit.framework.eval.node.scorer.model;

/**
 * 量规维度分数综合策略
 * <p>
 * 所有策略在计算前均先对每个维度得分做归一化（score / maxScore → [0,1]），
 * 再进行聚合，确保不同量程的维度（如 1~5 分 vs 0/1）之间权重对等。
 */
public enum RubricMergeStrategy {

    /**
     * 加权平均: Σ(normalizedScore_i × weight_i) / Σ(weight_i)
     * 适用场景：各维度重要程度不同，需要通过 weight 显式体现。
     */
    WEIGHTED_AVERAGE,

    /**
     * 简单平均: Σ(normalizedScore_i) / N
     * 适用场景：各维度重要程度相同，不需要区分权重。
     */
    SIMPLE_AVERAGE,

    /**
     * 逻辑合取（AND）: 任意维度归一化得分 小于 passRate 则取所有归一化分的最小值，否则取加权均值
     * 适用场景：要求所有维度都达标，任一短板直接拉低整体分。
     */
    LOGICAL_AND,

    /**
     * 关键优先 + 顺序门控（Star Gate）:
     * 按顺序检查 star=true 的维度，任一 star 维度归一化分为 0 则直接返回 0，
     * 所有 star 通过后取剩余维度的加权均值。
     * 适用场景：存在一票否决项（如安全合规类维度）。
     */
    STAR_GATE,

    /**
     * 严格完成率: count(normalizedScore_i >= passRate_i) / N
     * 适用场景：关注"有多少比例的维度达标"，而非具体分值。
     */
    COMPLETION_RATE
}

