package com.evalkit.framework.workflow;

import com.evalkit.framework.workflow.model.DAG;
import com.evalkit.framework.workflow.model.WorkflowNode;

import java.util.Collection;

/**
 * 通用工作流构建器
 */
public class WorkflowBuilder {
    private final DAG dag = new DAG();

    /**
     * 添加单节点
     */
    private <T extends WorkflowNode> void addNode(T workflowNode) {
        dag.addTask(workflowNode);
    }

    /**
     * 批量添加节点
     */
    private <T extends WorkflowNode> WorkflowBuilder addNodes(Collection<? extends T> workflowNodes) {
        workflowNodes.forEach(this::addNode);
        return this;
    }

    private final <T extends WorkflowNode> void addNodes(T... workflowNodes) {
        for (T workflowNode : workflowNodes) {
            if (!dag.containsTask(workflowNode)) {
                addNode(workflowNode);
            }
        }
    }

    /**
     * 添加节点关系:一对一
     */
    private WorkflowBuilder link(String from, String to) {
        dag.addEdge(from, to);
        return this;
    }

    public <T extends WorkflowNode> WorkflowBuilder link(T from, T to) {
        addNodes(from, to);
        this.link(from.getId(), to.getId());
        return this;
    }

    /**
     * 添加节点关系:一对多
     */
    public <T extends WorkflowNode> WorkflowBuilder link(T from, Collection<? extends T> tos) {
        tos.forEach(to -> this.link(from, to));
        return this;
    }

    /**
     * 添加节点关系:多对一
     */
    public <T extends WorkflowNode> WorkflowBuilder link(Collection<? extends T> froms, T to) {
        froms.forEach(from -> this.link(from, to));
        return this;
    }

    /**
     * 添加节点关系:多对多
     */
    public <T extends WorkflowNode> WorkflowBuilder link(Collection<? extends T> froms, Collection<? extends T> tos) {
        froms.forEach(from -> this.link(from, tos));
        return this;
    }

    /**
     * 添加节点关系: 单节点串联
     */
    @SafeVarargs
    public final <T extends WorkflowNode> WorkflowBuilder link(T... nodes) {
        if (nodes.length == 0) {
            throw new IllegalArgumentException("The number of nodes must be greater than 0");
        }
        T prev = nodes[0];
        for (int i = 1; i < nodes.length; i++) {
            T cur = nodes[i];
            link(prev, cur);
            prev = cur;
        }
        return this;
    }

    /**
     * 添加节点关系:多节点串联
     */
    @SafeVarargs
    public final <T extends WorkflowNode> WorkflowBuilder link(Collection<? extends T>... nodeLists) {
        if (nodeLists.length == 0) {
            throw new IllegalArgumentException("The number of nodes must be greater than 0");
        }
        Collection<? extends T> prev = nodeLists[0];
        for (int i = 1; i < nodeLists.length; i++) {
            Collection<? extends T> cur = nodeLists[i];
            link(prev, cur);
            prev = cur;
        }
        return this;
    }

    /**
     * 构建DAG图
     */
    public Workflow build() {
        return new Workflow(dag);
    }
}
