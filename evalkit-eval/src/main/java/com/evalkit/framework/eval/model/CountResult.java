package com.evalkit.framework.eval.model;

import com.evalkit.framework.workflow.model.WorkflowContext;

/**
 * 统计结果抽象接口
 */
public interface CountResult {
    /**
     * 结果写入上下文
     */
    void writeToCtx(WorkflowContext ctx);

    /**
     * 获取统计名称
     */
    String counterName();
}
