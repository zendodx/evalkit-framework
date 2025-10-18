package com.evalkit.framework.eval.node.scorer.checker.model;

import com.evalkit.framework.eval.node.scorer.checker.constants.CheckItemReason;
import com.evalkit.framework.eval.node.scorer.checker.constants.CheckMethod;
import lombok.Builder;
import lombok.Data;

/**
 * 检查项
 */
@Data
public class CheckItem {
    /* 检查项名称 */
    private String name;
    /* 检查项分数 */
    private double score;
    /* 检查理由 */
    private String reason;
    /* 检查项权重 */
    private double weight;
    /* 是否为必过检查 */
    private boolean star;
    /* 是否支持评测,如果不支持会给默认值 */
    private boolean support;
    /* 是否执行过 */
    private boolean executed;
    /* 默认值,不执行评测或者评测失败时的取值 */
    private double defaultScore;
    /* 检查描述,描述检查项的打分策略,可用于大模型的参考 */
    private String checkDescription;
    /* 检查方法: LLM检查, 规则检查 */
    private CheckMethod checkMethod;

    /**
     * 初始化检查项
     */
    public CheckItem(String name, String checkDescription) {
        this(name, 1, false, true, 0, checkDescription);
    }

    public CheckItem(String name, boolean star, String checkDescription) {
        this(name, 1, star, true, 0, checkDescription);
    }

    public CheckItem(String name, double weight, String checkDescription) {
        this(name, weight, false, true, 0, checkDescription);
    }

    public CheckItem(String name, double weight, boolean star, String checkDescription) {
        this(name, weight, star, true, 0, checkDescription);
    }

    public CheckItem(String name, double weight, boolean star, double defaultScore, String checkDescription) {
        this(name, weight, star, true, defaultScore, checkDescription);
    }

    @Builder
    public CheckItem(String name, double weight, boolean star, boolean support, double defaultScore, String checkDescription) {
        this.name = name;
        this.weight = weight;
        this.star = star;
        this.support = support;
        this.defaultScore = defaultScore;
        this.checkDescription = checkDescription;
        // 不支持评测时设置分数为默认值,检查理由是不支持检查
        this.score = support ? 0 : defaultScore;
        this.reason = support ? CheckItemReason.NO_CHECK : CheckItemReason.UN_SUPPORT;
        this.executed = false;
        this.checkMethod = CheckMethod.NONE;
    }

    /**
     * 计算权重分数
     */
    public double getWeightScore() {
        return score * weight;
    }
}
