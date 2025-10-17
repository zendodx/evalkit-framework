package com.evalkit.framework.workflow;

import com.evalkit.framework.workflow.model.WorkflowContext;

/**
 * 工作流上下文ThreadLocal
 */
public class WorkflowContextHolder {
    private static final ThreadLocal<WorkflowContext> TL = new ThreadLocal<>();

    private WorkflowContextHolder() {
    }

    public static void set(WorkflowContext ctx) {
        TL.set(ctx);
    }

    public static WorkflowContext get() {
        return TL.get();
    }

    public static void clear() {
        TL.remove();
    }
}
