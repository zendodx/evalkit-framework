package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.scorer.config.ScorerConfig;
import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 基于prompt的评估器
 */
@Slf4j
public abstract class PromptBasedScorer extends Scorer {
    /* 大模型服务 */
    protected final LLMService llmService;

    public PromptBasedScorer(ScorerConfig config, LLMService llmService) {
        super(config);
        this.llmService = llmService;
    }

    /**
     * 准备系统提示词
     */
    public abstract String prepareSysPrompt();

    /**
     * 准备用户提示词
     */
    public abstract String prepareUserPrompt(InputData inputData, ApiCompletionResult apiCompletionResult);

    /**
     * 解析大模型回复
     */
    public abstract LLMResult parseLLMReply(String reply);

    @Override
    public ScorerResult eval(DataItem dataItem) {
        InputData inputData = dataItem.getInputData();
        ApiCompletionResult apiCompletionResult = dataItem.getApiCompletionResult();
        String sysPrompt = prepareSysPrompt();
        String userPrompt = prepareUserPrompt(inputData, apiCompletionResult);
        // 拼接系统提示词和用户提示词
        String prompt = sysPrompt + "\n----------以下是输入数据----------\n" + userPrompt;
        String llmReply;
        try {
            llmReply = llmService.chat(prompt);
            log.info("LLM service chat success, prompt: {}, llm reply: {}", prompt, llmReply);
            LLMResult checkResult = parseLLMReply(llmReply);
            Map<String, Object> extraInfo = new HashMap<>();
            extraInfo.put("prompt", prompt);
            extraInfo.put("llmReply", llmReply);
            ScorerResult scorerResult = new ScorerResult();
            scorerResult.setMetric(config.getMetricName());
            scorerResult.setScore(checkResult.score);
            scorerResult.setReason(checkResult.reason);
            scorerResult.setExtra(extraInfo);
            return scorerResult;
        } catch (Exception e) {
            log.error("LLM service chat error, prompt: {}", prompt, e);
            throw e;
        }
    }

    /**
     * 大模型结果
     */
    @Data
    public static class LLMResult {
        private Double score;
        private String reason;
    }
}
