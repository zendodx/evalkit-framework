package com.evalkit.framework.workflow.model;


import com.evalkit.framework.workflow.exception.DAGException;
import lombok.Data;

import java.util.*;

/**
 * DAG模型
 */
@Data
public class DAG {
    private final Map<String, WorkflowNode> tasks = new HashMap<>();
    private final Map<String, Set<String>> inEdges = new HashMap<>();
    private final Map<String, Set<String>> outEdges = new HashMap<>();

    /**
     * 是否包含节点
     */
    public boolean containsTask(WorkflowNode workflowNode) {
        return tasks.containsKey(workflowNode.getId());
    }

    /**
     * 添加节点
     */
    public void addTask(WorkflowNode workflowNode) {
        tasks.put(workflowNode.getId(), workflowNode);
        inEdges.putIfAbsent(workflowNode.getId(), new HashSet<>());
        outEdges.putIfAbsent(workflowNode.getId(), new HashSet<>());
    }

    /**
     * 添加节点依赖(边)
     */
    public void addEdge(String from, String to) {
        if (!tasks.containsKey(from) || !tasks.containsKey(to)) {
            throw new DAGException("DAG graph add dependency failed as from node or to node not exists");
        }
        inEdges.get(to).add(from);
        outEdges.get(from).add(to);
    }

    public Set<String> getInEdges(String taskId) {
        return inEdges.getOrDefault(taskId, Collections.emptySet());
    }

    public Set<String> getOutEdges(String taskId) {
        return outEdges.getOrDefault(taskId, Collections.emptySet());
    }

    public WorkflowNode getTask(String taskId) {
        return tasks.get(taskId);
    }

    public Collection<WorkflowNode> getAllTasks() {
        return tasks.values();
    }

    /**
     * from和to是否关联
     */
    public boolean hasEdge(String from, String to) {
        return outEdges.getOrDefault(from, Collections.emptySet()).contains(to);
    }
}