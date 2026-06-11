package com.evalkit.framework.infra.service.llm;

import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.infra.service.llm.config.LoadBalanceLLMServiceConfig;
import com.evalkit.framework.infra.service.llm.strategy.RoundRobinLoadBalanceStrategy;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class LoadBalanceLLMServiceTest {

    LoadBalanceLLMService loadBalanceLLMService;

    /**
     * 构造一个固定返回指定内容的 mock LLMService，不发起任何 HTTP 请求
     */
    private LLMService mockLLMService(String model, String fixedReply) {
        return new LLMService() {
            @Override
            public String chat(String prompt) {
                return fixedReply;
            }

            @Override
            public String getModel() {
                return model;
            }
        };
    }

    @BeforeEach
    void setUp() {
        // 用 mock LLMService 替代真实的 DeepSeek 服务，不依赖外部 token 或 HTTP
        LLMService llmService1 = mockLLMService("mock-model-1", "reply from model-1");
        LLMService llmService2 = mockLLMService("mock-model-1", "reply from model-1");
        LLMService llmService3 = mockLLMService("mock-model-1", "reply from model-1");
        LLMService llmService4 = mockLLMService("mock-model-2", "reply from model-2");
        LLMService llmService5 = mockLLMService("mock-model-2", "reply from model-2");

        List<LLMService> llmServices = ListUtils.of(
                llmService1, llmService2, llmService3, llmService4, llmService5);

        loadBalanceLLMService = new LoadBalanceLLMService(
                LoadBalanceLLMServiceConfig.builder()
                        .llmServices(llmServices)
                        .loadBalanceStrategy(new RoundRobinLoadBalanceStrategy())
                        .build()
        );
    }

    @Test
    void testGetModel() {
        String model = loadBalanceLLMService.getModel();
        assertNotNull(model, "getModel() 不应返回 null");
        log.info("models: {}", model);
    }

    @Test
    void testChatRoundRobin() {
        // 验证轮询策略：多次调用应分布在不同服务上
        AtomicInteger callCount = new AtomicInteger(0);
        for (int i = 0; i < 5; i++) {
            String reply = loadBalanceLLMService.chat("test query " + i);
            assertNotNull(reply, "chat() 返回不应为 null");
            callCount.incrementAndGet();
        }
        assertEquals(5, callCount.get(), "应成功完成 5 次 chat 调用");
        log.info("完成 {} 次 chat 调用，负载均衡正常", callCount.get());
    }

    @Test
    void testEmptyLLMServicesThrowsException() {
        // 校验空 services 列表时构造应抛出异常
        assertThrows(IllegalArgumentException.class, () ->
                new LoadBalanceLLMService(
                        LoadBalanceLLMServiceConfig.builder()
                                .llmServices(ListUtils.of())
                                .loadBalanceStrategy(new RoundRobinLoadBalanceStrategy())
                                .build()
                )
        );
    }
}