package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.scorer.config.ScorerConfig;
import com.evalkit.framework.infra.service.llm.LLMServiceFactory;
import org.junit.jupiter.api.Test;

class PromptBasedScorerTest {
    void test() {
        PromptBasedScorer promptBasedScorer = new PromptBasedScorer(
                ScorerConfig.builder().build(),
                LLMServiceFactory.createLLMService("test", null)
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