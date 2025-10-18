package com.evalkit.framework.workflow.constants;

import lombok.Getter;

/**
 * 工作流状态枚举
 */
@Getter
public enum WorkflowStatus {
    INIT,
    RUNNING,
    DONE,
    FAILED
}