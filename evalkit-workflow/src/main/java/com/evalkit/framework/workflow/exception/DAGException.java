package com.evalkit.framework.workflow.exception;

/**
 * DAG异常
 */
public class DAGException extends RuntimeException {

    public DAGException(String message) {
        super(message);
    }

    public DAGException(String message, Exception e) {
        super(message, e);
    }
}