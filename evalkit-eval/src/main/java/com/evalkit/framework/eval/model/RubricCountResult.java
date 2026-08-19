package com.evalkit.framework.eval.model;

import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.workflow.model.WorkflowContext;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Rubric 评估器指标统计结果。
 * <p>
 * 两级结构：
 * <ul>
 *   <li>{@link RubricMetricGroup}：评估器级（每个 metricName 一组）</li>
 *   <li>{@link CriteriaGroup}：维度级（每个 criteriaName 一组）</li>
 * </ul>
 */
@Data
public class RubricCountResult implements CountResult {

    /**
     * 统计结果名称（用于上下文 key）
     */
    private final String counterName = "rubricCountResult";

    /**
     * 评估器统计分组列表
     */
    private List<RubricMetricGroup> metricGroups = new ArrayList<>();

    // ==================== 内部类 ====================

    /**
     * 评估器级统计分组（对应一个 metricName）。
     */
    @Data
    public static class RubricMetricGroup {

        /**
         * 评估器名称（metricName）
         */
        private String metricName;

        /**
         * 样本总数
         */
        private int totalCount;

        /**
         * 通过数（ScorerResult.pass == true）
         */
        private int passCount;

        /**
         * 失败数
         */
        private int failCount;

        /**
         * 通过率
         */
        private double passRate;

        /**
         * 失败率
         */
        private double failRate;

        /**
         * 最终分均值（归一化后）
         */
        private double avgScore;

        /**
         * 最终分最低值
         */
        private double minScore;

        /**
         * 最终分最高值
         */
        private double maxScore;

        /**
         * 各维度统计分组
         */
        private List<CriteriaGroup> criteriaGroups = new ArrayList<>();
    }

    /**
     * 维度级统计分组（对应一个 criteriaName）。
     */
    @Data
    public static class CriteriaGroup {

        /**
         * 维度名称
         */
        private String criteriaName;

        /**
         * 打分指引（分级依据标准），对应 {@code RubricCriteria.scoringGuide}
         */
        private String scoringGuide;

        /**
         * 原始分均值
         */
        private double avgRawScore;

        /**
         * 归一化分均值
         */
        private double avgNormScore;

        /**
         * 通过阈值（passScore / maxScore，来自 extra）
         */
        private double passThreshold;

        /**
         * 该维度达标样本数（normScore >= passThreshold）
         */
        private int passCount;

        /**
         * 该维度未达标样本数
         */
        private int failCount;

        /**
         * 该维度通过率
         */
        private double passRate;

        /**
         * 该维度失败率
         */
        private double failRate;

        /**
         * 各样本在该维度的打分明细（用于报告层下钻）
         */
        private List<CriteriaDataPoint> dataPoints = new ArrayList<>();
    }

    /**
     * 单个样本在某个维度上的打分数据点。
     */
    @Data
    public static class CriteriaDataPoint {

        /**
         * 样本序号（与 DataItem.dataIndex 对应）
         */
        private Long dataIndex;

        /**
         * 该维度原始分
         */
        private double rawScore;

        /**
         * 该维度归一化分
         */
        private double normScore;

        /**
         * 打分一句话理由
         */
        private String reason;

        /**
         * 是否通过（normScore >= passThreshold）
         */
        private boolean passed;
    }

    // ==================== CountResult 接口实现 ====================

    @Override
    public void writeToCtx(WorkflowContext ctx) {
        WorkflowContextOps.setCountResult(ctx, this);
    }

    @Override
    public String counterName() {
        return counterName;
    }
}

