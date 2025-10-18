package com.evalkit.framework.workflow.exception;

/**
 * 工作流异常
 */
public class WorkflowException extends RuntimeException {
    public WorkflowException(String message) {
        super(message);
    }

    public WorkflowException(String message, Exception e) {
        super(message, e);
    }
}
