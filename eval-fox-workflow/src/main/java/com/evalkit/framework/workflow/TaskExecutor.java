package com.evalkit.framework.workflow;

import com.evalkit.framework.workflow.model.DAG;
import com.evalkit.framework.workflow.model.WorkflowNode;
import com.evalkit.framework.workflow.utils.GraphUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 工作流节点执行器
 */
@Slf4j
public class TaskExecutor {
    private final ExecutorService executorService;

    public TaskExecutor(int threadPoolSize) {
        executorService = Executors.newFixedThreadPool(threadPoolSize);
    }

    public void executeTasks(DAG dag) throws ExecutionException, InterruptedException {
        Map<String, Future<Object>> futures = new HashMap<>();
        List<String> sortedTasks = GraphUtils.topologicalSort(dag);
        try {
            for (String taskId : sortedTasks) {
                WorkflowNode workflowNode = dag.getTask(taskId);
                Set<String> dependencies = dag.getInEdges(taskId);
                for (String dependency : dependencies) {
                    Future<Object> dependencyFuture = futures.get(dependency);
                    if (dependencyFuture != null) {
                        dependencyFuture.get();
                    }
                }
                futures.put(taskId, executorService.submit(workflowNode));
            }
        } finally {
            for (Future<Object> future : futures.values()) {
                future.get();
            }
            shutdown();
        }
    }

    public void shutdown() {
        executorService.shutdown();
    }
}
