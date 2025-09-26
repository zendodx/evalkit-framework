package com.evalkit.framework.workflow;

import com.evalkit.framework.workflow.constants.WorkflowStatus;
import com.evalkit.framework.workflow.model.WorkflowContext;
import com.evalkit.framework.workflow.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class WorkflowBuilderTest {
    @Test
    public void testWorkflow() {
        WorkflowNode node1 = new WorkflowNode() {
            @Override
            protected void doExecute() {
                WorkflowContext ctx = WorkflowContextHolder.get();
                ctx.put("node1", 1);
                System.out.println("Execute node1");
            }
        };

        WorkflowNode node2 = new WorkflowNode() {
            @Override
            protected void doExecute() {
                WorkflowContext ctx = WorkflowContextHolder.get();
                ctx.put("node2", 2);
                System.out.println("Execute node2");
            }
        };

        WorkflowNode node3 = new WorkflowNode() {
            @Override
            protected void doExecute() {
                WorkflowContext ctx = WorkflowContextHolder.get();
                ctx.put("node3", 3);
                System.out.println("Execute node3");
            }
        };


        WorkflowBuilder builder = new WorkflowBuilder();
        Workflow workflow = builder.link(node1, node2, node3).build();
        workflow.execute();

        WorkflowStatus status = workflow.getStatus();
        assertEquals(WorkflowStatus.DONE, status);
        WorkflowContext ctx = workflow.getWorkflowContext();
        List<String> keys = ctx.keys();
        assertEquals(3, keys.size());
        log.info("Workflow context: {}", ctx);
    }
}