package com.evalkit.framework.eval.node.begin;

import com.evalkit.framework.eval.constants.NodeNamePrefix;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.node.begin.config.BeginConfig;
import com.evalkit.framework.eval.node.scorer.strategy.EvalReasonStrategy;
import com.evalkit.framework.eval.node.scorer.strategy.LLMSummaryEvalReasonStrategy;
import com.evalkit.framework.infra.service.llm.LLMService;
import com.evalkit.framework.workflow.model.WorkflowContext;
import com.evalkit.framework.workflow.model.WorkflowNode;
import com.evalkit.framework.workflow.utils.WorkflowUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

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
        validConfig(config);
        this.config = config;
    }

    protected void validConfig(BeginConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("BeginConfig can not be null");
        }
        if (config.getScoreStrategy() == null) {
            throw new IllegalArgumentException("ScoreStrategy can not be null");
        }
        // 校验评测原因构建是否为null
        if (config.getEvalReasonStrategy() == null) {
            throw new IllegalArgumentException("EvalReasonStrategy can not be null");
        }
        // 如果是大模型评测原因构建策略,需要校验大模型服务和sysPrompt是否合法
        EvalReasonStrategy evalReasonStrategy = config.getEvalReasonStrategy();
        if (evalReasonStrategy instanceof LLMSummaryEvalReasonStrategy) {
            LLMSummaryEvalReasonStrategy llmSummaryEvalReasonStrategy = (LLMSummaryEvalReasonStrategy) evalReasonStrategy;
            LLMService llmService = llmSummaryEvalReasonStrategy.getLlmService();
            String sysPrompt = llmSummaryEvalReasonStrategy.getSysPrompt();
            if (llmService == null) {
                throw new IllegalArgumentException("EvalReasonStrategy LLMService can not be null");
            }
            if (StringUtils.isEmpty(sysPrompt)) {
                throw new IllegalArgumentException("EvalReasonStrategy sysPrompt can not be null");
            }
        }
    }

    /**
     * 初始化评测工作流上下文
     */
    protected void initWorkflowContext() {
        WorkflowContext ctx = getWorkflowContext();
        if (ctx == null) {
            throw new EvalException("WorkflowContext is null");
        }
        WorkflowContextOps.setScorerStrategy(ctx, config.getScoreStrategy());
        WorkflowContextOps.setEvalReasonStrategy(ctx, config.getEvalReasonStrategy());
        WorkflowContextOps.setThreshold(ctx, config.getThreshold());
        if (CollectionUtils.isEmpty(WorkflowContextOps.getDataItems(ctx))) {
            WorkflowContextOps.setDataItems(ctx, new CopyOnWriteArrayList<>());
        }
        WorkflowContextOps.setCountResults(ctx, new ConcurrentHashMap<>());
        WorkflowContextOps.setExtra(ctx, new ConcurrentHashMap<>());
    }


    @Override
    protected void doExecute() {
        initWorkflowContext();
        log.info("Init workflow success, start execute");
    }
}
