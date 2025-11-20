package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.scorer.config.PromptBasedScorerConfig;
import com.evalkit.framework.infra.service.llm.LLMServiceFactory;

class PromptBasedScorerTest {
    void test() {
        PromptBasedScorer promptBasedScorer = new PromptBasedScorer(
                PromptBasedScorerConfig.builder()
                        .llmService(LLMServiceFactory.createLLMService("test", null))
                        .build()
        ) {
            @Override
            public String prepareSysPrompt() {
                return "";
            }

            @Override
            public String prepareUserPrompt(InputData inputData, ApiCompletionResult apiCompletionResult) {
                return "";
            }

            @Override
            public LLMResult parseLLMReply(String reply) {
                return null;
            }
        };
    }
}