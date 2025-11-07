package com.evalkit.framework.infra.service.llm;

import com.evalkit.framework.infra.service.llm.config.LLMServiceConfig;
import com.evalkit.framework.infra.service.llm.constants.LLMResponseType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Slf4j
class AbstractLLMServiceTest {
    static class TestLLMService extends AbstractLLMService {
        public TestLLMService(LLMServiceConfig config) {
            super(config);
        }

        @Override
        public String doChat(String prompt) {
            return "hi";
        }
    }

    /**
     * 测试错误JSON响应
     */
    @Test
    @Disabled
    void testErrorJsonResponse() {
        TestLLMService llmService = new TestLLMService(
                LLMServiceConfig.builder()
                        .model("test")
                        .responseType(LLMResponseType.JSON)
                        .build()
        );
        String response = llmService.chat("hello");
        log.info("llm response: {}", response);
    }
}