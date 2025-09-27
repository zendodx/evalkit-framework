package com.evalkit.framework.workflow;

import com.evalkit.framework.workflow.model.WorkflowContext;

/**
 * 静态ThreadLocal包装器
 */
public final class WorkflowContextHolder {
    // 使用InheritableThreadLocal保证跨线程访问
    private static final ThreadLocal<WorkflowContext> TL = new InheritableThreadLocal<>();

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
