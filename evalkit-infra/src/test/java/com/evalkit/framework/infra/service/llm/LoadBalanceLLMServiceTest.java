package com.evalkit.framework.infra.service.llm;

import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.common.utils.runtime.RuntimeEnvUtils;
import com.evalkit.framework.infra.service.llm.config.DeepseekLLMServiceConfig;
import com.evalkit.framework.infra.service.llm.config.LLMServiceConfig;
import com.evalkit.framework.infra.service.llm.config.LoadBalanceLLMServiceConfig;
import com.evalkit.framework.infra.service.llm.strategy.RoundRobinLoadBalanceStrategy;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

@Slf4j
class LoadBalanceLLMServiceTest {

    LoadBalanceLLMService loadBalanceLLMService;

    @BeforeEach
    void setUp() {
        String deepSeekToken = RuntimeEnvUtils.getPropertyFromResource("secret.properties", "deepseek-token");

        // 注册
        LLMServiceFactory.registerLLMService("DeepSeek_Test1", (LLMServiceFactory.LLMServiceBuilder<LLMService, LLMServiceConfig>) config -> new DeepSeekLLMService((DeepseekLLMServiceConfig) config));
        LLMServiceFactory.registerLLMService("DeepSeek_Test2", (LLMServiceFactory.LLMServiceBuilder<LLMService, LLMServiceConfig>) config -> new DeepSeekLLMService((DeepseekLLMServiceConfig) config));

        // 创建
        DeepseekLLMServiceConfig config = DeepseekLLMServiceConfig.builder()
                .apiToken(deepSeekToken)
                .build();
        LLMService llmService10 = LLMServiceFactory.createLLMService("DeepSeek_Test1", config);
        LLMService llmService11 = LLMServiceFactory.createLLMService("DeepSeek_Test1", config);
        LLMService llmService12 = LLMServiceFactory.createLLMService("DeepSeek_Test1", config);
        LLMService llmService13 = LLMServiceFactory.createLLMService("DeepSeek_Test1", config);
        LLMService llmService14 = LLMServiceFactory.createLLMService("DeepSeek_Test1", config);
        LLMService llmService20 = LLMServiceFactory.createLLMService("DeepSeek_Test2", config);
        LLMService llmService21 = LLMServiceFactory.createLLMService("DeepSeek_Test2", config);

        // 负载
        List<LLMService> llmServices = ListUtils.of(llmService10, llmService11, llmService12, llmService13, llmService14, llmService20, llmService21);
        loadBalanceLLMService = new LoadBalanceLLMService(
                LoadBalanceLLMServiceConfig.builder()
                        .llmServices(llmServices)
                        .loadBalanceStrategy(new RoundRobinLoadBalanceStrategy())
                        .build()
        );
    }

    @Test
    void test() {
        String model = loadBalanceLLMService.getModel();
        log.info("models: {}", model);
        loadBalanceLLMService.chat("hello,world");
        loadBalanceLLMService.chat("今日天气");
    }
}