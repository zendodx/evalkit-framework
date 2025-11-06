package com.evalkit.framework.eval.node.dataloader.datagen.querygen;

import com.evalkit.framework.common.utils.runtime.RuntimeEnvUtils;
import com.evalkit.framework.eval.node.dataloader.datagen.querygen.config.PromptBasedQueryGeneratorConfig;
import com.evalkit.framework.infra.service.llm.LLMService;
import com.evalkit.framework.infra.service.llm.LLMServiceFactory;
import com.evalkit.framework.infra.service.llm.config.DeepseekLLMServiceConfig;
import com.evalkit.framework.infra.service.llm.constants.LLMServiceEnum;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;

@Slf4j
class PromptBasedQueryGeneratorTest {
    @Test
    void test() {
        String deepSeekToken = RuntimeEnvUtils.getPropertyFromResource("secret.properties", "deepseek-token");
        LLMService llmService = LLMServiceFactory.createLLMService(
                LLMServiceEnum.DEEPSEEK.name(),
                DeepseekLLMServiceConfig.builder()
                        .apiToken(deepSeekToken)
                        .build()
        );

        PromptBasedQueryGenerator promptBasedQueryGenerator = new PromptBasedQueryGenerator(
                PromptBasedQueryGeneratorConfig.builder()
                        .llmService(llmService)
                        .genCount(2)
                        .userPrompt("关键词: 预订机票")
                        .build()
        );
        List<String> queries = promptBasedQueryGenerator.generate();
        log.info("queries: {}", queries);
    }
}