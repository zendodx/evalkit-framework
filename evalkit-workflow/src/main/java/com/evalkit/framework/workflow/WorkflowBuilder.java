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
     * 添加节点关系:单节点和多节点混合串联
     * 支持传入单个节点或节点集合的任意组合
     */
    @SuppressWarnings("unchecked")
    public final <T extends WorkflowNode> WorkflowBuilder link(Object... nodeItems) {
        if (nodeItems.length == 0) {
            throw new IllegalArgumentException("The number of node items must be greater than 0");
        }
        Object prev = nodeItems[0];
        if (prev instanceof Collection) {
            Collection<? extends T> prevCollection = (Collection<? extends T>) prev;
            addNodes(prevCollection);
        } else if (prev instanceof WorkflowNode) {
            T prevNode = (T) prev;
            addNode(prevNode);
        } else {
            throw new IllegalArgumentException("Node items must be WorkflowNode or Collection<? extends WorkflowNode>");
        }
        for (int i = 1; i < nodeItems.length; i++) {
            Object cur = nodeItems[i];
            // 验证当前节点类型
            if (!(cur instanceof Collection || cur instanceof WorkflowNode)) {
                throw new IllegalArgumentException("Node items must be WorkflowNode or Collection<? extends WorkflowNode>");
            }
            // 建立依赖关系
            if (prev instanceof Collection && cur instanceof Collection) {
                // 集合对集合
                Collection<? extends T> prevCollection = (Collection<? extends T>) prev;
                Collection<? extends T> curCollection = (Collection<? extends T>) cur;
                addNodes(curCollection);
                link(prevCollection, curCollection);
                prev = curCollection;
            } else if (prev instanceof Collection && cur instanceof WorkflowNode) {
                // 集合对单节点
                Collection<? extends T> prevCollection = (Collection<? extends T>) prev;
                T curNode = (T) cur;
                addNode(curNode);
                link(prevCollection, curNode);
                prev = curNode;
            } else if (prev instanceof WorkflowNode && cur instanceof Collection) {
                // 单节点对集合
                T prevNode = (T) prev;
                Collection<? extends T> curCollection = (Collection<? extends T>) cur;
                addNodes(curCollection);
                link(prevNode, curCollection);
                prev = curCollection;
            } else {
                // 单节点对单节点
                T prevNode = (T) prev;
                T curNode = (T) cur;
                addNode(curNode);
                link(prevNode, curNode);
                prev = curNode;
            }
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
