package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.common.utils.runtime.RuntimeEnvUtils;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.scorer.config.PromptBasedScorerConfig;
import com.evalkit.framework.infra.service.llm.DeepSeekLLMService;
import com.evalkit.framework.infra.service.llm.LLMService;
import com.evalkit.framework.infra.service.llm.LLMServiceFactory;
import com.evalkit.framework.infra.service.llm.config.DeepseekLLMServiceConfig;
import com.evalkit.framework.infra.service.llm.config.LLMServiceConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
class GSBScorerTest {
    LLMService llmService;

    @BeforeEach
    void setUp() {
        String deepSeekToken = RuntimeEnvUtils.getPropertyFromResource("secret.properties", "deepseek-token");
        LLMServiceFactory.registerLLMService("DeepSeek_Test", (LLMServiceFactory.LLMServiceBuilder<LLMService, LLMServiceConfig>) config -> new DeepSeekLLMService((DeepseekLLMServiceConfig) config));
        DeepseekLLMServiceConfig config = DeepseekLLMServiceConfig.builder()
                .apiToken(deepSeekToken)
                .build();
        llmService = LLMServiceFactory.createLLMService("DeepSeek_Test", config);
    }

    @Test
    void test() {
        GSBScorer gsbScorer = new GSBScorer(
                PromptBasedScorerConfig.builder()
                        .llmService(llmService)
                        .build()
        ) {
            @Override
            public String prepareGoldAnswer(InputData inputData, ApiCompletionResult apiCompletionResult) {
                return "乔布斯是美国人";
            }

            @Override
            public String prepareCandidateAnswer(InputData inputData, ApiCompletionResult apiCompletionResult) {
                return "乔布美国人";
            }

            @Override
            public String prepareInput(InputData inputData, ApiCompletionResult apiCompletionResult) {
                return "乔布斯是非洲人";
            }
        };
        DataItem dataItem = new DataItem();
        dataItem.setInputData(new InputData());
        dataItem.setApiCompletionResult(new ApiCompletionResult());
        ScorerResult scorerResult = gsbScorer.eval(dataItem);
        log.error("scorerResult:{}", scorerResult);
    }
}