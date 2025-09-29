package com.evalkit.framework.infra.service.llm;


import com.evalkit.framework.infra.service.llm.config.DeepseekLLMServiceConfig;
import com.evalkit.framework.infra.service.llm.config.LLMServiceConfig;
import com.evalkit.framework.infra.service.llm.constants.LLMServiceEnum;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 大模型服务构建工厂
 */
public class LLMServiceFactory {
    /* 模型注册池 */
    private static final Map<String, LLMServiceBuilder<?, ?>> registry = new LinkedHashMap<>();

    /* 构造器接口 */
    public interface LLMServiceBuilder<T extends LLMService, C extends LLMServiceConfig> {
        T build(C config);
    }

    static {
        // 预注册DeepSeek服务
        registerLLMService(LLMServiceEnum.DEEPSEEK.name(), new LLMServiceBuilder<DeepseekLLMService, DeepseekLLMServiceConfig>() {
            @Override
            public DeepseekLLMService build(DeepseekLLMServiceConfig config) {
                return new DeepseekLLMService(config);
            }
        });
    }

    /**
     * 注册大模型服务
     */
    public static <T extends LLMService, C extends LLMServiceConfig> void registerLLMService(String serviceName, LLMServiceBuilder<T, C> serviceBuilder) {
        registry.put(serviceName, serviceBuilder);
    }

    /**
     * 创建大模型服务
     */
    @SuppressWarnings("unchecked")
    public static <T extends LLMService, C extends LLMServiceConfig> T createLLMService(String serviceName, C serviceConfig) {
        LLMServiceBuilder<T, C> serviceBuilder = (LLMServiceBuilder<T, C>) registry.get(serviceName);
        if (serviceBuilder == null) {
            throw new IllegalArgumentException("Invalid LLM service name: " + serviceName);
        }
        return serviceBuilder.build(serviceConfig);
    }
}
