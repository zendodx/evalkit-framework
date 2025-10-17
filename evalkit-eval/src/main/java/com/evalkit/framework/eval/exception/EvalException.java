package com.evalkit.framework.eval.exception;

/**
 * 评测异常类
 */
public class EvalException extends RuntimeException {

    public EvalException(String message) {
        super(message);
    }

    public EvalException(String message, Exception e) {
        super(message, e);
    }
}
