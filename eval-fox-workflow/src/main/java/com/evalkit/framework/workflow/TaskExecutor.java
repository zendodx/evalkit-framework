package com.evalkit.framework.workflow;

import com.evalkit.framework.workflow.model.DAG;
import com.evalkit.framework.workflow.model.WorkflowContext;
import com.evalkit.framework.workflow.model.WorkflowNode;
import com.evalkit.framework.workflow.utils.GraphUtils;
import lombok.Getter;
import lombok.Setter;
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
    // 新增开关, 控制手动关闭线程池
    @Setter
    @Getter
    private boolean autoShutdown;

    public TaskExecutor(int threadPoolSize) {
        this.autoShutdown = true;
        executorService = Executors.newFixedThreadPool(threadPoolSize);
    }

    public TaskExecutor(int threadPoolSize, boolean autoShutdown) {
        this.autoShutdown = autoShutdown;
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
    }

    public void executeTasks(DAG dag, WorkflowContext workflowContext) throws ExecutionException, InterruptedException {
        Map<String, Future<Object>> futures = new HashMap<>();
        List<String> sortedTasks = GraphUtils.topologicalSort(dag);
        try {
            for (String taskId : sortedTasks) {
                WorkflowNode workflowNode = dag.getTask(taskId);
                workflowNode.setWorkflowContext(workflowContext);
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
            // 只有允许自动关闭时才关闭
            if (autoShutdown) {
                shutdown();
            }
        }
    }

    public void shutdown() {
        executorService.shutdown();
    }
}
