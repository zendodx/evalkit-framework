package com.evalkit.framework.eval.facade.config;

import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.workflow.Workflow;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 增量评测配置
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class DeltaEvalConfig extends EvalConfig {
    /* 数据加载器 */
    private DataLoader dataLoader;
    /* 评测工作流,必填 */
    private Workflow evalWorkflow;
    /* 评测结果上报工作流,必填 */
    private Workflow reportWorkflow;
    /* 批处理数量,默认1 */
    @Builder.Default
    int batchSize = 1;
    /* 结果上报间隔,默认30秒 */
    @Builder.Default
    int reportInterval = 30;
    /* MQ消息接收超时时间,默认10000毫秒 */
    @Builder.Default
    int mqReceiveTimeout = 10000;
    /* 是否开启断点续评, 默认true */
    @Builder.Default
    boolean enableResume = true;
}
