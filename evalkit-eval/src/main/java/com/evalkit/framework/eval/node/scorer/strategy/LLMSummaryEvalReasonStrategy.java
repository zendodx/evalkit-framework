package com.evalkit.framework.eval.node.scorer.strategy;

import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 大模型总结的评测原因构建策略
 * <p>
 * 功能: 以文本格式返回整体理由, 将各评估器的评估理由拼接成一段话, 然后使用大模型总结理由
 */
@Slf4j
@Data
public class LLMSummaryEvalReasonStrategy implements EvalReasonStrategy {
    protected static final String DEFAULT_SYS_PROMPT = "请把下方全文浓缩成一段逻辑连贯、信息完整的话：保留唯一核心论点、关键数据与结论，删除例子、修饰与细节；禁止出现“本文”“作者”等字样，禁止演绎或添加原文未提及信息。";
    protected LLMService llmService;
    protected String sysPrompt;

    public LLMSummaryEvalReasonStrategy(LLMService llmService) {
        this(llmService, DEFAULT_SYS_PROMPT);
    }

    public LLMSummaryEvalReasonStrategy(LLMService llmService, String sysPrompt) {
        this.llmService = llmService;
        this.sysPrompt = sysPrompt;
    }

    @Override
    public String buildEvalReason(List<ScorerResult> scorerResults) {
        StringBuilder sb = new StringBuilder();
        for (ScorerResult scorerResult : scorerResults) {
            sb.append(scorerResult.getReason()).append(" | ");
        }
        try {
            String prompt = sysPrompt + "\n-----输入文本如下----\n" + sb;
            return llmService.chat(prompt);
        } catch (Exception e) {
            log.error("LLMSummaryEvalReasonStrategy build eval reason failed, error:{}", e.getMessage(), e);
            return sb.toString();
        }
    }

    @Override
    public String getStrategyName() {
        return "大模型总结评测原因构建策略";
    }
}
