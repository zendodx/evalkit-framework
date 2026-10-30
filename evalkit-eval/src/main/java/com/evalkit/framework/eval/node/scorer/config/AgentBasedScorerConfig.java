package com.evalkit.framework.eval.node.scorer.config;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.concurrent.TimeUnit;

/**
 * 基于Agent的评估器配置
 * <p>
 * Agent任务通常是异步长耗时任务(提交任务后需轮询等待执行结束),
 * 因此额外提供轮询间隔、轮询超时等配置项。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class AgentBasedScorerConfig extends ScorerConfig {
    /* 轮询间隔,默认10 */
    @Builder.Default
    protected long pollInterval = 10;
    /* 轮询间隔时间单位,默认秒 */
    @Builder.Default
    protected TimeUnit pollIntervalTimeUnit = TimeUnit.SECONDS;
    /* 轮询超时时间,超过该时间任务仍未结束则判定为超时失败,默认600 */
    @Builder.Default
    protected long pollTimeout = 600;
    /* 轮询超时时间单位,默认秒 */
    @Builder.Default
    protected TimeUnit pollTimeoutTimeUnit = TimeUnit.SECONDS;
}

