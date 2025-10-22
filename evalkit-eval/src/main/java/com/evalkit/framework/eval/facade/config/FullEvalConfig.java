package com.evalkit.framework.eval.facade.config;

import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.workflow.Workflow;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 全量式评测配置
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class FullEvalConfig extends EvalConfig {
    /* 数据加载器 */
    private DataLoader dataLoader;
    /* 评测工作流 */
    private Workflow evalWorkflow;
    /* 结果上报工作流 */
    private Workflow reportWorkflow;
}
