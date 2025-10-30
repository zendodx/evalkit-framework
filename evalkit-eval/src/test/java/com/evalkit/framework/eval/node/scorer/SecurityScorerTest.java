package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.scorer.config.ScorerConfig;
import com.evalkit.framework.infra.service.llm.LLMServiceFactory;
import org.junit.jupiter.api.Test;

class SecurityScorerTest {
    void test() {
        SecurityScorer securityScorer = new SecurityScorer(
                ScorerConfig.builder().build(),
                LLMServiceFactory.createLLMService("test", null)
        ) {
            @Override
            public String prepareUserPrompt(InputData inputData, ApiCompletionResult apiCompletionResult) {
                return "";
            }
        };
    }
}