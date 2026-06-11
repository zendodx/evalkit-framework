package com.evalkit.framework.infra.service.llm;

import com.evalkit.framework.infra.service.llm.config.LLMServiceConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class LLMServiceFactoryTest {

    /**
     * 构造一个固定回复的 mock LLMService，不依赖任何外部服务
     */
    private LLMService mockLLMService(String fixedReply) {
        return new LLMService() {
            @Override
            public String chat(String prompt) {
                return fixedReply;
            }

            @Override
            public String getModel() {
                return "mock-model";
            }
        };
    }

    @Test
    void testRegisterAndCreateLLMService() {
        // 使用 mock builder 注册服务，不依赖任何外部 token 或 HTTP 请求
        LLMServiceFactory.registerLLMService("Mock_Test",
                (LLMServiceFactory.LLMServiceBuilder<LLMService, LLMServiceConfig>)
                        config -> mockLLMService("hello from mock"));

        // 创建服务实例
        LLMService llmService = LLMServiceFactory.createLLMService("Mock_Test",
                LLMServiceConfig.builder().model("mock-model").build());

        assertNotNull(llmService, "创建的 LLMService 不应为 null");

        // 验证 mock 调用可以正常返回，而不会真正发起 HTTP 请求
        String reply = llmService.chat("hello");
        assertEquals("hello from mock", reply, "mock LLMService 应返回预期的固定回复");
        log.info("llmService model:{}, reply:{}", llmService.getModel(), reply);
    }

    @Test
    void testCreateUnregisteredServiceThrowsException() {
        // 访问未注册的服务名称，应抛出 IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> LLMServiceFactory.createLLMService("NonExistentService", null),
                "访问未注册服务应抛出 IllegalArgumentException");
    }

    @Test
    void testRegisterOverwriteExistingService() {
        // 先注册一个返回 "v1" 的服务
        LLMServiceFactory.registerLLMService("Override_Test",
                (LLMServiceFactory.LLMServiceBuilder<LLMService, LLMServiceConfig>)
                        config -> mockLLMService("v1"));
        LLMService v1 = LLMServiceFactory.createLLMService("Override_Test",
                LLMServiceConfig.builder().model("mock").build());
        assertEquals("v1", v1.chat("test"));

        // 覆盖注册为返回 "v2" 的服务
        LLMServiceFactory.registerLLMService("Override_Test",
                (LLMServiceFactory.LLMServiceBuilder<LLMService, LLMServiceConfig>)
                        config -> mockLLMService("v2"));
        LLMService v2 = LLMServiceFactory.createLLMService("Override_Test",
                LLMServiceConfig.builder().model("mock").build());
        assertEquals("v2", v2.chat("test"), "覆盖注册后，新服务应返回新的回复");
    }
}