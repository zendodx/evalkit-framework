package com.evalkit.framework.workflow.utils;

import com.evalkit.framework.workflow.exception.DAGException;
import com.evalkit.framework.workflow.model.DAG;
import com.evalkit.framework.workflow.model.WorkflowNode;

import java.util.*;

/**
 * DAG图工具类
 */
public class GraphUtils {
    private GraphUtils() {
    }

    /**
     * 拓扑排序
     */
    public static List<String> topologicalSort(DAG dag) {
        Map<String, Integer> inDegree = new HashMap<>();
        Collection<WorkflowNode> allWorkflowNodes = dag.getAllTasks();

        for (WorkflowNode workflowNode : allWorkflowNodes) {
            inDegree.put(workflowNode.getId(), dag.getInEdges(workflowNode.getId()).size());
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        List<String> sortedTasks = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sortedTasks.add(current);
            for (String neighbor : dag.getOutEdges(current)) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        if (sortedTasks.size() != dag.getAllTasks().size()) {
            throw new DAGException("DAG graph has cycle");
        }
        return sortedTasks;
    }
}
