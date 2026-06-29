package com.evalkit.framework.workflow.model;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * 通用节点配置基类
 * <p>
 * 所有节点专属 Config 均可继承此类，以获得通用配置能力。
 * 当前支持配置：
 * <ul>
 *   <li>{@code threadNum} — 节点内部批处理的并发线程数，默认 1。</li>
 *   <li>{@code batchTimeoutSec} — 批处理单条数据超时时间（秒），
 *       即 {@link com.evalkit.framework.common.thread.BatchRunner#runBatch} 中
 *       每条数据允许执行的最长秒数（总超时 = 数据条数 × batchTimeoutSec）。
 *       默认值 600 秒（10 分钟），与框架历史行为保持一致。</li>
 * </ul>
 */
@Data
@SuperBuilder
public class NodeConfig {

    /**
     * 节点内部批处理并发线程数，默认 1（串行）。
     * 对应 {@link com.evalkit.framework.common.thread.BatchRunner#runBatch} 的 threadNum 参数。
     */
    @Builder.Default
    protected int threadNum = 1;

    /**
     * 批处理单条数据超时（秒）。
     * 总超时 = 数据条数 × batchTimeoutSec。
     * 默认 600 秒（30 分钟），与框架历史行为保持一致。
     */
    @Builder.Default
    protected long batchTimeoutSec = 60 * 30;
}

