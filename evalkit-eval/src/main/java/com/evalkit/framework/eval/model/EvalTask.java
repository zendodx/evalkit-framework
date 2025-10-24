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
    private String taskName;
    private long allCount;
    private int status;
    private Date createTime;
    private Date updateTime;
    private Date finishTime;
}