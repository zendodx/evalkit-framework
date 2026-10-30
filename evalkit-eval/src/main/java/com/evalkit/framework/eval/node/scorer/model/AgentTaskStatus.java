package com.evalkit.framework.eval.node.scorer.model;

/**
 * Agent任务执行状态
 */
public enum AgentTaskStatus {
    /* 任务执行中,需要继续轮询 */
    RUNNING,
    /* 任务执行成功 */
    SUCCESS,
    /* 任务执行失败 */
    FAILED,
    /* 轮询超时,任务仍未结束 */
    TIMEOUT
}