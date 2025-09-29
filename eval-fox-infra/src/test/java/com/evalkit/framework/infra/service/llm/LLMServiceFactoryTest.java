package com.evalkit.framework.infra.service.llm;

import com.evalkit.framework.infra.service.llm.config.DeepseekLLMServiceConfig;
import com.evalkit.framework.infra.service.llm.config.LLMServiceConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
class LLMServiceFactoryTest {
    @Test
    public void test() {
        // 注册DeepSeek_Test大模型服务
        LLMServiceFactory.registerLLMService("DeepSeek_Test", new LLMServiceFactory.LLMServiceBuilder<LLMService, LLMServiceConfig>() {
            @Override
            public LLMService build(LLMServiceConfig config) {
                return new DeepseekLLMService((DeepseekLLMServiceConfig) config);
            }
        });

        // 创建服务实例
        DeepseekLLMServiceConfig config = DeepseekLLMServiceConfig.builder()
                .apiToken("xxx")
                .build();
        LLMService llmService = LLMServiceFactory.createLLMService("DeepSeek_Test", config);

        String query = "hello";
        String reply = llmService.chat(query);
        log.info("llm service config:{}, query:{}, reply:{}", config, query, reply);
    }
}