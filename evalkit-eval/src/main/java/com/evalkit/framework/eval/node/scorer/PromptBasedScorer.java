package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.scorer.config.PromptBasedScorerConfig;
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
    protected final PromptBasedScorerConfig config;

    public PromptBasedScorer(PromptBasedScorerConfig config) {
        super(config);
        validConfig(config);
        this.config = config;
    }

    protected void validConfig(PromptBasedScorerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config is null");
        }
        if (config.getLlmService() == null) {
            throw new IllegalArgumentException("LLMService is null");
        }
        if (config.getRetryTimes() < 0) {
            throw new IllegalArgumentException("RetryTimes must more than 0");
        }
        if (config.getRetryTimeUnit() == null) {
            throw new IllegalArgumentException("RetryTimeUnit is null");
        }
        if (config.getRetryInterval() < 0) {
            throw new IllegalArgumentException("RetryInterval must more than 0");
        }
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
        String llmReply = "";
        boolean enableRetry = config.isEnableRetry();
        int retryTimes = config.getRetryTimes();
        Exception lastException = null;
        try {
            int curRetryTimes = 0;
            LLMResult checkResult = null;
            // 解析失败后重试
            do {
                LLMService llmService = config.getLlmService();
                try {
                    llmReply = llmService.chat(prompt);
                    checkResult = parseLLMReply(llmReply);
                    log.info("LLM service chat success, prompt: {}, llm reply: {}, chat result: {}", prompt, llmReply, checkResult);
                    break;
                } catch (Exception e) {
                    log.error("Parse LLM reply failed, retry times: {}, error: {}", curRetryTimes, e.getMessage(), e);
                    lastException = e;
                    curRetryTimes++;
                    try {
                        Thread.sleep(config.getRetryTimeUnit().toMillis(config.getRetryInterval()));
                    } catch (Exception ignored) {

                    }
                }
            } while (enableRetry && curRetryTimes < retryTimes);
            // 重试后失败跑出异常
            if (checkResult == null) {
                String errorMsg;
                if (lastException == null) {
                    errorMsg = String.format("Parse LLM reply failed after retry, retry times: %s, last error: %s", retryTimes, "unknown");
                } else {
                    errorMsg = String.format("Parse LLM reply failed after retry, retry times: %s, last error: %s", retryTimes, lastException.getMessage());
                }
                throw new EvalException(errorMsg);
            }
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
