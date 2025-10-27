package com.evalkit.framework.eval.node.scorer.checker.model;

import com.evalkit.framework.eval.node.scorer.checker.constants.CheckItemReason;
import com.evalkit.framework.eval.node.scorer.checker.constants.CheckMethod;
import org.apache.commons.lang3.StringUtils;

/**
 * 检查项
 */
public class CheckItem {
    /* 检查项名称,默认未命名检查项 */
    protected String name = "未命名检查项";
    /* 检查项得分,默认0 */
    protected double score = 0;
    /* 检查项总分数,默认0*/
    protected double totalScore = 0;
    /* 检查理由,默认"" */
    protected String reason = "未执行加床";
    /* 检查项权重,默认1.0 */
    protected double weight = 1.0;
    /* 是否为必过检查,默认false */
    protected boolean star = false;
    /* 是否支持评测,如果不支持会给默认值,默认true */
    protected boolean support = true;
    /* 是否执行过 */
    protected boolean executed;
    /* 默认值,不执行评测或者评测失败时的取值,默认0.0 */
    protected double defaultScore = 0.0;
    /* 检查描述,描述检查项的打分策略,可用于大模型的参考 */
    protected String checkDescription;
    /* 检查方法: LLM检查, 规则检查 */
    protected CheckMethod checkMethod;

    protected CheckItem(String name, double totalScore, double weight, boolean star, boolean support, double defaultScore, String checkDescription) {
        this.name = name;
        this.totalScore = totalScore;
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

        // 构造后额外操作
        checkParams();
    }

    /**
     * 计算权重分数
     */
    public double getWeightScore() {
        return score * weight;
    }

    /**
     * 参数校验
     */
    protected void checkParams() {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("检查项名称不能为空");
        }
        if (totalScore < 0) {
            throw new IllegalArgumentException("检查项总分不能小于0");
        }
        if (weight < 0) {
            throw new IllegalArgumentException("检查项权重不能小于0");
        }
        if (defaultScore < 0) {
            throw new IllegalArgumentException("检查项默认值不能小于0");
        }
    }

    /* ------ 构造器 ----- */

    public static CheckItemBuilder<?> builder() {
        return new CheckItemBuilder<>();
    }

    protected static class CheckItemBuilder<B extends CheckItemBuilder<B>> {
        protected String name = "未命名检查项";
        protected double totalScore = 0;
        protected double weight = 1.0;
        protected boolean star = false;
        protected boolean support = true;
        protected double defaultScore = 0.0;
        protected String checkDescription;

        protected CheckItemBuilder() {
        }

        public B name(String name) {
            this.name = name;
            return (B) this;
        }

        public B totalScore(double totalScore) {
            this.totalScore = totalScore;
            return (B) this;
        }

        public B weight(double weight) {
            this.weight = weight;
            return (B) this;
        }

        public B star(boolean star) {
            this.star = star;
            return (B) this;
        }

        public B support(boolean support) {
            this.support = support;
            return (B) this;
        }


        public B defaultScore(double defaultScore) {
            this.defaultScore = defaultScore;
            return (B) this;
        }

        public B checkDescription(String checkDescription) {
            this.checkDescription = checkDescription;
            return (B) this;
        }

        public CheckItem build() {
            return new CheckItem(name, totalScore, weight, star, support, defaultScore, checkDescription);
        }
    }

    /* ----- getter/setter ----- */

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public double getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(double totalScore) {
        this.totalScore = totalScore;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public boolean isStar() {
        return star;
    }

    public void setStar(boolean star) {
        this.star = star;
    }

    public boolean isSupport() {
        return support;
    }

    public void setSupport(boolean support) {
        this.support = support;
    }

    public boolean isExecuted() {
        return executed;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
    }

    public double getDefaultScore() {
        return defaultScore;
    }

    public void setDefaultScore(double defaultScore) {
        this.defaultScore = defaultScore;
    }

    public String getCheckDescription() {
        return checkDescription;
    }

    public void setCheckDescription(String checkDescription) {
        this.checkDescription = checkDescription;
    }

    public CheckMethod getCheckMethod() {
        return checkMethod;
    }

    public void setCheckMethod(CheckMethod checkMethod) {
        this.checkMethod = checkMethod;
    }
}
