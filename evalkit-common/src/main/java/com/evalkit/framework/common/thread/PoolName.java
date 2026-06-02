package com.evalkit.framework.common.thread;

public enum PoolName {
    /**
     * 数据加载线程池
     */
    DATA_WRAPPER,
    /**
     * 接口调用线程池
     */
    API_COMPLETION,
    /**
     * 评估器专用线程池
     */
    SCORER,
    /**
     * Rubric 评估器内部：各维度并发 LLM 调用专用线程池
     * 与 SCORER 池隔离，避免外层 SCORER 任务占满线程池后内层维度任务无线程可用的死锁
     */
    SCORER_CRITERIA,
    /**
     * MQ消息消费线程池
     */
    MQ_CONSUME,
    /**
     * 数据生成线程池
     */
    DATA_GENERATOR
}
