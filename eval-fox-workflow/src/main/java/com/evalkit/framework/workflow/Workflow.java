package com.evalkit.framework.workflow;

import com.evalkit.framework.workflow.constants.WorkflowStatus;
import com.evalkit.framework.workflow.exception.WorkflowException;
import com.evalkit.framework.workflow.model.DAG;
import com.evalkit.framework.workflow.model.WorkflowContext;
import lombok.Data;

/**
 * 基于DAG的工作流
 */
@Data
public class Workflow {
    private DAG dag;
    private WorkflowContext workflowContext;
    private WorkflowStatus status;
    private TaskExecutor taskExecutor;

    public Workflow(DAG dag) {
        this.dag = dag;
        this.taskExecutor = new TaskExecutor(Runtime.getRuntime().availableProcessors());
        this.workflowContext = new WorkflowContext();
        init();
    }

    public void init() {
        try {
            WorkflowContextHolder.set(this.workflowContext);
            setStatus(WorkflowStatus.INIT);
        } catch (Exception e) {
            setStatus(WorkflowStatus.FAILED);
            throw new WorkflowException("Init workflow error:" + e.getMessage(), e);
        }
    }

    public void execute() {
        try {
            setStatus(WorkflowStatus.RUNNING);
            taskExecutor.executeTasks(dag);
            setStatus(WorkflowStatus.DONE);
        } catch (Exception e) {
            setStatus(WorkflowStatus.FAILED);
            throw new WorkflowException("Execute workflow error:" + e.getMessage(), e);
        }
    }

    public void stop() {
        if (taskExecutor != null) {
            taskExecutor.shutdown();
        }
    }
}
