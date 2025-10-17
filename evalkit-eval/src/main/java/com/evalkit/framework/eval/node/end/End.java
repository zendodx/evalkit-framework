package com.evalkit.framework.eval.node.end;


import com.evalkit.framework.eval.constants.NodeNamePrefix;
import com.evalkit.framework.workflow.model.WorkflowContext;
import com.evalkit.framework.workflow.model.WorkflowNode;
import com.evalkit.framework.workflow.utils.WorkflowUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流结束节点
 */
@Slf4j
public abstract class End extends WorkflowNode {

    public End() {
        super(WorkflowUtils.generateNodeId(NodeNamePrefix.END));
    }

    public abstract void process(WorkflowContext ctx);

    @Override
    protected void doExecute() {
        process(getWorkflowContext());
        log.info("End execute workflow");
    }
}
