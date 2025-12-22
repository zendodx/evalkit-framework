package com.evalkit.framework.eval.model;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * 评测任务实体
 */
@Data
@Builder
public class EvalTask {
    /* 任务名称 */
    private String taskName;
    /* 任务名称对应的uuid */
    private String taskNameUuid;
    /* 评测任务数据量 */
    private long allCount;
    /* 评测任务状态 */
    private int status;
    /* 创建时间 */
    private Date createTime;
    /* 更新时间 */
    private Date updateTime;
    /* 完成时间 */
    private Date finishTime;
}