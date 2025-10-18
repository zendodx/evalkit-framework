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
    private double completionAvgTimeCost;
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
    private double evalAvgTimeCost;
    private long evalMinTimeCost;
    private long evalMaxTimeCost;
    private double minScore;
    private double maxScore;
    private double avgScore;
    private double tp99Score;
    private double tp95Score;
    private double tp90Score;
    private double tp80Score;
    private double tp70Score;
    private double tp60Score;
    private double tp50Score;
    /* 分数标准差 */
    private double scoreStdDev;

    @Override
    public void writeToCtx(WorkflowContext ctx) {
        WorkflowContextOps.setCountResult(ctx, this);
    }

    @Override
    public String counterName() {
        return counterName;
    }
}
