package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.scorer.config.PromptBasedScorerConfig;
import com.evalkit.framework.infra.service.llm.LLMService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SecurityScorerTest {

    /**
     * 构造一个 mock LLMService，返回安全评分 JSON 格式（符合 SecurityScorer 的期望）
     */
    private LLMService buildMockLLMService() {
        return new LLMService() {
            @Override
            public String chat(String prompt) {
                // 返回符合 SecurityScorer parseLLMReply 期望的 JSON 格式
                return "{\"score\":1,\"reason\":\"内容安全，无违规信息\"}";
            }

            @Override
            public String getModel() {
                return "mock-model";
            }
        };
    }

    @Test
    void testConstructSecurityScorer() {
        SecurityScorer securityScorer = new SecurityScorer(
                PromptBasedScorerConfig.builder()
                        .llmService(buildMockLLMService())
                        .build()
        ) {
            @Override
            public String prepareUserPrompt(InputData inputData, ApiCompletionResult apiCompletionResult) {
                return "测试文本：你好，今天天气真好！";
            }
        };

        assertNotNull(securityScorer, "SecurityScorer 实例不应为 null");
    }

    @Test
    void testEvalWithMockLLM() {
        SecurityScorer securityScorer = new SecurityScorer(
                PromptBasedScorerConfig.builder()
                        .llmService(buildMockLLMService())
                        .metricName("安全检查")
                        .totalScore(1)
                        .enableRetry(false)
                        .build()
        ) {
            @Override
            public String prepareUserPrompt(InputData inputData, ApiCompletionResult apiCompletionResult) {
                return "测试文本：你好，今天天气真好！";
            }
        };

        DataItem dataItem = new DataItem();
        dataItem.setInputData(new InputData());
        dataItem.setApiCompletionResult(new ApiCompletionResult());

        ScorerResult result = securityScorer.eval(dataItem);
        assertNotNull(result, "评分结果不应为 null");
        assertEquals(1.0, result.getScore(), 1e-6, "安全内容应得满分");
    }
}