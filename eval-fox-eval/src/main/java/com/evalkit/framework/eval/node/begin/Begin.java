package com.evalkit.framework.eval.node.begin;

import com.evalkit.framework.eval.constants.NodeNamePrefix;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.node.begin.config.BeginConfig;
import com.evalkit.framework.workflow.model.WorkflowContext;
import com.evalkit.framework.workflow.model.WorkflowNode;
import com.evalkit.framework.workflow.utils.WorkflowUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 开始节点,初始化评测工作流上下文
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class Begin extends WorkflowNode {
    protected BeginConfig config;

    public Begin() {
        this.config = BeginConfig.builder().build();
    }

    public Begin(BeginConfig config) {
        super(WorkflowUtils.generateNodeId(NodeNamePrefix.BEGIN));
        this.config = config;
    }

    /**
     * 初始化评测工作流上下文
     */
    protected void initWorkflowContext() {
        WorkflowContext ctx = getWorkflowContext();
        WorkflowContextOps.setTaskName(ctx, config.getTaskName());
        WorkflowContextOps.setScorerStrategy(ctx, config.getScoreStrategy());
        WorkflowContextOps.setThreshold(ctx, config.getThreshold());
        if (CollectionUtils.isEmpty(WorkflowContextOps.getDataItems(ctx))) {
            WorkflowContextOps.setDataItems(ctx, new CopyOnWriteArrayList<>());
        }
        WorkflowContextOps.setCountResults(ctx, new ConcurrentHashMap<>());
    }


    @Override
    protected void doExecute() {
        initWorkflowContext();
        log.info("Init workflow success, start execute");
    }
}
