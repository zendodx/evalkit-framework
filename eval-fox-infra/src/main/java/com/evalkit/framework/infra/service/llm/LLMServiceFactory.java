package com.evalkit.framework.infra.service.llm;


import com.evalkit.framework.infra.service.llm.config.DeepseekLLMServiceConfig;
import com.evalkit.framework.infra.service.llm.config.LLMServiceConfig;
import com.evalkit.framework.infra.service.llm.constants.LLMServiceType;

import java.util.Objects;

/**
 * 大模型服务构建工厂
 */
public class LLMServiceFactory {
    /**
     * 根据传入的类型和模型构造大模型服务
     */
    public static LLMService createLLMService(LLMServiceType type, LLMServiceConfig config) {
        if (Objects.requireNonNull(type) == LLMServiceType.DEEPSEEK) {
            return createDeepseekLLMService(config);
        }
        return null;
    }

    /**
     * 构造deepseek大模型服务
     */
    private static DeepseekLLMService createDeepseekLLMService(LLMServiceConfig config) {
        return new DeepseekLLMService((DeepseekLLMServiceConfig) config);
    }
}
