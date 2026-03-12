package com.evalkit.framework.eval.model;

import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.workflow.model.WorkflowContext;
import lombok.Data;

import java.util.List;

/**
 * 评估指标统计结果
 */
@Data
public class MetricCountResult implements CountResult {
    /* 统计结果名称 */
    private final String counterName = "metricCountResult";
    /* 评估指标分组 */
    private List<MetricGroup> metricGroups;

    /**
     * 评估指标分组
     */
    @Data
    public static class MetricGroup {
        private String metricName;
        private Double minValue;
        private Double maxValue;
        private Double avgValue;
        private Long passCount;
        private Long failCount;
        private Double passRate;
        private Double failRate;
        private List<MetricItem> passMetricItems;
        private List<MetricItem> failMetricItems;
    }

    @Override
    public void writeToCtx(WorkflowContext ctx) {
        WorkflowContextOps.setCountResult(ctx, this);
    }

    @Override
    public String counterName() {
        return counterName;
    }
}