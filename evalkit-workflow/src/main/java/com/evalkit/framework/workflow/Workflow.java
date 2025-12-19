package com.evalkit.framework.workflow;

import com.evalkit.framework.workflow.constants.WorkflowStatus;
import com.evalkit.framework.workflow.exception.WorkflowException;
import com.evalkit.framework.workflow.model.DAG;
import com.evalkit.framework.workflow.model.WorkflowContext;
import lombok.Data;

/**
 * 基于DAG的通用工作流
 */
@Data
public class Workflow implements Cloneable {
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
            setStatus(WorkflowStatus.INIT);
        } catch (Exception e) {
            setStatus(WorkflowStatus.FAILED);
            throw new WorkflowException("Init workflow error:" + e.getMessage(), e);
        }
    }

    public void execute() {
        try {
            setStatus(WorkflowStatus.RUNNING);
            taskExecutor.executeTasks(dag, workflowContext);
            setStatus(WorkflowStatus.DONE);
        } catch (Exception e) {
            setStatus(WorkflowStatus.FAILED);
            throw new WorkflowException("Execute workflow error:" + e.getMessage(), e);
        } finally {
            try {
                stop();
                WorkflowContextHolder.clear();
            } catch (Exception ignore) {

            }
        }
    }

    public void stop() {
        if (taskExecutor != null && taskExecutor.isAutoShutdown()) {
            taskExecutor.shutdown();
        }
    }

    /**
     * 线程池是否自动关闭
     */
    public void setAutoShutdown(boolean autoShutdown) {
        taskExecutor.setAutoShutdown(autoShutdown);
    }


    @Override
    public Workflow clone() {
        try {
            Workflow clone = (Workflow) super.clone();
            if (this.dag != null) {
                clone.setDag(this.dag.clone());
            }
            if (this.workflowContext != null) {
                clone.setWorkflowContext(this.workflowContext.clone());
            }
            clone.status = this.status;
            // 克隆时创建新的TaskExecutor，避免共享线程池
            clone.taskExecutor = new TaskExecutor(
                Runtime.getRuntime().availableProcessors(),
                this.taskExecutor.isAutoShutdown()
            );
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
