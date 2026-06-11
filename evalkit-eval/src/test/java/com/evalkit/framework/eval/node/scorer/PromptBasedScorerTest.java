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

class PromptBasedScorerTest {

    /**
     * 构造一个 mock LLMService，返回符合 PromptBasedScorer.LLMResult 格式的 JSON
     */
    private LLMService buildMockLLMService() {
        return new LLMService() {
            @Override
            public String chat(String prompt) {
                // 返回符合 LLMResult（包含 score 和 reason 字段）的 JSON
                return "{\"score\":0.8,\"reason\":\"回复基本符合预期\"}";
            }

            @Override
            public String getModel() {
                return "mock-model";
            }
        };
    }

    @Test
    void testConstructPromptBasedScorer() {
        PromptBasedScorer promptBasedScorer = new PromptBasedScorer(
                PromptBasedScorerConfig.builder()
                        .llmService(buildMockLLMService())
                        .build()
        ) {
            @Override
            public String prepareSysPrompt() {
                return "你是一个评分助手";
            }

            @Override
            public String prepareUserPrompt(InputData inputData, ApiCompletionResult apiCompletionResult) {
                return "问题: hello\n答案: world";
            }

            @Override
            public LLMResult parseLLMReply(String reply) {
                // 使用 setter 方法（@Data 生成 private 字段的 getter/setter）
                LLMResult result = new LLMResult();
                result.setScore(0.8);
                result.setReason("mock reason");
                return result;
            }
        };

        assertNotNull(promptBasedScorer, "PromptBasedScorer 实例不应为 null");
    }

    @Test
    void testEvalWithMockLLM() {
        PromptBasedScorer promptBasedScorer = new PromptBasedScorer(
                PromptBasedScorerConfig.builder()
                        .llmService(buildMockLLMService())
                        .metricName("相关性检查")
                        .totalScore(1)
                        .enableRetry(false)
                        .build()
        ) {
            @Override
            public String prepareSysPrompt() {
                return "你是一个评分助手";
            }

            @Override
            public String prepareUserPrompt(InputData inputData, ApiCompletionResult apiCompletionResult) {
                return "问题: hello\n答案: world";
            }

            @Override
            public LLMResult parseLLMReply(String reply) {
                LLMResult result = new LLMResult();
                result.setScore(0.8);
                result.setReason("回复基本符合预期");
                return result;
            }
        };

        DataItem dataItem = new DataItem();
        dataItem.setInputData(new InputData());
        dataItem.setApiCompletionResult(new ApiCompletionResult());

        ScorerResult result = promptBasedScorer.eval(dataItem);
        assertNotNull(result, "评分结果不应为 null");
        assertEquals(0.8, result.getScore(), 1e-6, "评分应为 0.8");
        assertEquals("相关性检查", result.getMetric(), "指标名应正确");
    }
}