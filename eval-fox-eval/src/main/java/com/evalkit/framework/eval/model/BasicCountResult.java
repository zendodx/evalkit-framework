package com.evalkit.framework.eval.model;

import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.workflow.model.WorkflowContext;
import lombok.Data;

/**
 * 基础统计结果
 */
@Data
public class BasicCountResult implements CountResult {
    /* 统计结果名称 */
    private final String counterName = "basicCountResult";
    /* 统计类型 */
    private int type = 0;
    private String typeName = "基础统计";
    /* 评测通过率/不通过率/跳过率/接口调用失败率/评测失败率统计 */
    private double passRate;
    private double unPassRate;
    private double completionErrorRate;
    private double completionSuccessRate;
    private double evalErrorRate;
    private double evalSuccessRate;
    private int passCount;
    private int unPassCount;
    private int totalCount;
    private int completionErrorCount;
    private int completionSuccessCount;
    private int evalErrorCount;
    private int evalSuccessCount;
    /* 业务接口耗时统计, 单位:ms */
    private long completionAvgTimeCost;
    private long completionMinTimeCost;
    private long completionMaxTimeCost;
    private long completionTP99TimeCost;
    private long completionTP95TimeCost;
    private long completionTP90TimeCost;
    private long completionTP80TimeCost;
    private long completionTP70TimeCost;
    private long completionTP60TimeCost;
    private long completionTP50TimeCost;
    /* 评测耗时统计, 单位:ms */
    private long evalAvgTimeCost;
    private long evalMinTimeCost;
    private long evalMaxTimeCost;

    /**
     * 将另一个 BasicCountResult 合并到当前对象中。
     * 合并完成后，当前对象即为代表两者之和的最新结果。
     */
    public void merge(BasicCountResult other) {
        if (other == null) {
            return;
        }

        /* 1. 累加次数 */
        this.totalCount += other.totalCount;
        this.passCount += other.passCount;
        this.unPassCount += other.unPassCount;
        this.completionErrorCount += other.completionErrorCount;
        this.completionSuccessCount += other.completionSuccessCount;
        this.evalErrorCount += other.evalErrorCount;
        this.evalSuccessCount += other.evalSuccessCount;

        /* 2. 重新计算比率 */
        recalculateRates();

    /* 3. 耗时指标：这里只给出一种“加权平均 + 极值合并”示例，
          实际请根据你们是否保存了完整耗时列表来调整。
          如果拿不到明细数据，只能做近似合并。 */
        this.completionAvgTimeCost = weightedAvg(this.completionAvgTimeCost,
                this.totalCount - other.totalCount,
                other.completionAvgTimeCost,
                other.totalCount);

        this.completionMinTimeCost = Math.min(this.completionMinTimeCost, other.completionMinTimeCost);
        this.completionMaxTimeCost = Math.max(this.completionMaxTimeCost, other.completionMaxTimeCost);

        this.evalAvgTimeCost = weightedAvg(this.evalAvgTimeCost,
                this.evalSuccessCount + this.evalErrorCount - other.evalSuccessCount - other.evalErrorCount,
                other.evalAvgTimeCost,
                other.evalSuccessCount + other.evalErrorCount);

        this.evalMinTimeCost = Math.min(this.evalMinTimeCost, other.evalMinTimeCost);
        this.evalMaxTimeCost = Math.max(this.evalMaxTimeCost, other.evalMaxTimeCost);

    /* 4. 如果没有明细数据，TPxx 只能退化为 min/max/avg 的某种折中，
          或者干脆不合并（保留当前值）。这里示范用 max 做保守合并。 */
        this.completionTP99TimeCost = Math.max(this.completionTP99TimeCost, other.completionTP99TimeCost);
        this.completionTP95TimeCost = Math.max(this.completionTP95TimeCost, other.completionTP95TimeCost);
        this.completionTP90TimeCost = Math.max(this.completionTP90TimeCost, other.completionTP90TimeCost);
        this.completionTP80TimeCost = Math.max(this.completionTP80TimeCost, other.completionTP80TimeCost);
        this.completionTP70TimeCost = Math.max(this.completionTP70TimeCost, other.completionTP70TimeCost);
        this.completionTP60TimeCost = Math.max(this.completionTP60TimeCost, other.completionTP60TimeCost);
        this.completionTP50TimeCost = Math.max(this.completionTP50TimeCost, other.completionTP50TimeCost);

    }

    private void recalculateRates() {
        int total = this.totalCount;
        if (total == 0) {
            this.passRate = 0.0;
            this.unPassRate = 0.0;
            this.completionSuccessRate = 0.0;
            this.completionErrorRate = 0.0;
            this.evalSuccessRate = 0.0;
            this.evalErrorRate = 0.0;
            return;
        }
        this.passRate = (double) this.passCount / total;
        this.unPassRate = (double) this.unPassCount / total;

        int completionTotal = this.completionSuccessCount + this.completionErrorCount;
        if (completionTotal > 0) {
            this.completionSuccessRate = (double) this.completionSuccessCount / completionTotal;
            this.completionErrorRate = (double) this.completionErrorCount / completionTotal;
        }

        int evalTotal = this.evalSuccessCount + this.evalErrorCount;
        if (evalTotal > 0) {
            this.evalSuccessRate = (double) this.evalSuccessCount / evalTotal;
            this.evalErrorRate = (double) this.evalErrorCount / evalTotal;
        }
    }

    private static long weightedAvg(long avg1, int count1, long avg2, int count2) {
        if (count1 + count2 == 0) return 0;
        return (avg1 * count1 + avg2 * count2) / (count1 + count2);
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
