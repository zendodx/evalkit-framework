package com.evalkit.framework.eval.node.scorer.model;

/**
 * 量规评分类型
 */
public enum RubricScoreType {

    /**
     * 阶梯分: 在 [minScore, maxScore] 范围内取整数或小数分值
     * 示例: 1~5 分
     */
    STEPPED,

    /**
     * 二元分: 只能取 0 或 1
     * 示例: Pass/Fail、是否含有害内容
     */
    BINARY
}

