package com.evalkit.framework.eval.facade;

import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.workflow.Workflow;
import lombok.Builder;
import lombok.Data;

/**
 * 全量式评测配置
 */
@Data
@Builder
public class FullEvalConfig {
    /* 任务名称,必填 */
    private String taskName;
    /* 数据加载器 */
    private DataLoader dataLoader;
    /* 评测工作流 */
    private Workflow evalWorkflow;
    /* 结果上报工作流 */
    private Workflow reportWorkflow;
}
