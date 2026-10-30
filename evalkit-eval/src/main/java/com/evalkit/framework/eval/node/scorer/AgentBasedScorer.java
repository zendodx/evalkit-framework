package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.scorer.config.AgentBasedScorerConfig;
import com.evalkit.framework.eval.node.scorer.model.AgentExecutionResult;
import com.evalkit.framework.eval.node.scorer.model.AgentTask;
import com.evalkit.framework.eval.node.scorer.model.AgentTaskStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 基于Agent的评分器,通过接入外部/本地Agent进行深度评测
 * <p>
 * Agent任务通常是异步长耗时任务(如GUIAgent巡检作业),典型执行流程为:
 * 提交任务 -> 轮询等待任务结束 -> 拉取执行结果 -> 解析为评测结果。
 * 具体的Agent接入方式(HTTP调用外部Agent服务、拉起本地Agent进程等)由子类实现,
 * 本类只负责编排通用的执行骨架,不绑定任何具体Agent实现。
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public abstract class AgentBasedScorer extends Scorer {
    protected final AgentBasedScorerConfig config;

    public AgentBasedScorer(AgentBasedScorerConfig config) {
        super(config);
        validConfig(config);
        this.config = config;
        super.scorerType = "agentBasedScorer";
    }

    protected void validConfig(AgentBasedScorerConfig config) {
        super.validConfig(config);
        if (config.getPollInterval() <= 0) {
            throw new IllegalArgumentException("pollInterval must be more than 0");
        }
        if (config.getPollIntervalTimeUnit() == null) {
            throw new IllegalArgumentException("pollIntervalTimeUnit is null");
        }
        if (config.getPollTimeout() <= 0) {
            throw new IllegalArgumentException("pollTimeout must be more than 0");
        }
        if (config.getPollTimeoutTimeUnit() == null) {
            throw new IllegalArgumentException("pollTimeoutTimeUnit is null");
        }
    }

    /**
     * 准备下发给Agent的任务
     */
    public abstract AgentTask prepareAgentTask(InputData inputData, ApiCompletionResult apiCompletionResult);

    /**
     * 提交Agent任务,返回任务ID/句柄,用于后续轮询
     */
    public abstract String submitAgentTask(AgentTask task) throws Exception;

    /**
     * 轮询查询Agent任务当前执行状态
     */
    public abstract AgentTaskStatus pollAgentTask(String taskId) throws Exception;

    /**
     * 任务结束后拉取Agent执行结果详情
     */
    public abstract AgentExecutionResult fetchAgentResult(String taskId) throws Exception;

    /**
     * 解析Agent执行结果,构造评测结果
     */
    public abstract ScorerResult prepareScorerResult(InputData inputData, ApiCompletionResult apiCompletionResult, AgentExecutionResult agentResult);

    @Override
    public ScorerResult eval(DataItem dataItem) throws Exception {
        InputData inputData = dataItem.getInputData();
        ApiCompletionResult apiCompletionResult = dataItem.getApiCompletionResult();
        // 1.准备Agent任务
        AgentTask task = prepareAgentTask(inputData, apiCompletionResult);
        // 2.提交Agent任务
        String taskId = submitAgentTask(task);
        if (StringUtils.isEmpty(taskId)) {
            throw new EvalException("Submit agent task failed, taskId is empty, dataIndex=" + dataItem.getDataIndex());
        }
        log.info("Submit agent task success, dataIndex={}, taskId={}", dataItem.getDataIndex(), taskId);
        // 3.轮询等待任务结束
        AgentTaskStatus status = waitForAgentTask(taskId);
        // 4.拉取Agent执行结果
        AgentExecutionResult agentResult = fetchAgentResult(taskId);
        if (agentResult == null) {
            throw new EvalException("Fetch agent result is null, taskId=" + taskId);
        }
        // 兜底填充任务ID与状态,避免子类遗漏
        if (StringUtils.isEmpty(agentResult.getTaskId())) {
            agentResult.setTaskId(taskId);
        }
        if (agentResult.getStatus() == null) {
            agentResult.setStatus(status);
        }
        if (status != AgentTaskStatus.SUCCESS) {
            log.warn("Agent task not success, taskId={}, status={}, errorMsg={}", taskId, status, agentResult.getErrorMsg());
        }
        // 5.解析Agent执行结果为评测结果
        return prepareScorerResult(inputData, apiCompletionResult, agentResult);
    }

    /**
     * 轮询等待Agent任务结束(成功/失败),超时则判定为TIMEOUT
     */
    protected AgentTaskStatus waitForAgentTask(String taskId) throws Exception {
        long pollIntervalMs = config.getPollIntervalTimeUnit().toMillis(config.getPollInterval());
        long pollTimeoutMs = config.getPollTimeoutTimeUnit().toMillis(config.getPollTimeout());
        long deadline = System.currentTimeMillis() + pollTimeoutMs;
        while (true) {
            AgentTaskStatus status = pollAgentTask(taskId);
            if (status == null) {
                throw new EvalException("Poll agent task status is null, taskId=" + taskId);
            }
            if (status == AgentTaskStatus.SUCCESS || status == AgentTaskStatus.FAILED) {
                return status;
            }
            if (System.currentTimeMillis() >= deadline) {
                log.error("Poll agent task timeout, taskId={}, pollTimeoutMs={}", taskId, pollTimeoutMs);
                return AgentTaskStatus.TIMEOUT;
            }
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EvalException("Poll agent task interrupted, taskId=" + taskId, e);
            }
        }
    }
}