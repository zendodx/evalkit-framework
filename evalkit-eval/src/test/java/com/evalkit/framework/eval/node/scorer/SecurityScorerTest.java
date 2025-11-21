package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.scorer.config.PromptBasedScorerConfig;
import com.evalkit.framework.infra.service.llm.LLMServiceFactory;

class SecurityScorerTest {
    void test() {
        SecurityScorer securityScorer = new SecurityScorer(
                PromptBasedScorerConfig.builder()
                        .llmService(LLMServiceFactory.createLLMService("test", null))
                        .build()
        ) {
            @Override
            public String prepareUserPrompt(InputData inputData, ApiCompletionResult apiCompletionResult) {
                return "";
            }
        };
    }
}