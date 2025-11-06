package com.evalkit.framework.infra.service.llm;


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

    /**
     * 大模型服务构建器接口
     *
     * @param <T> 大模型服务类型, 实现LLMService接口
     * @param <C> 大模型服务配置类型, 继承LLMServiceConfig配置类
     */
    public interface LLMServiceBuilder<T extends LLMService, C extends LLMServiceConfig> {
        T build(C config);
    }

    /* 工厂初始化时预先注册默认支持的大模型服务 */
    static {
        // 预注册DeepSeek大模型服务
        registerLLMService(LLMServiceEnum.DEEPSEEK.name(), DeepSeekLLMService::new);
    }

    /**
     * 注册大模型服务
     *
     * @param serviceName    大模型服务名称
     * @param serviceBuilder 大模型服务构建器
     * @param <T>            大模型服务类型
     * @param <C>            大模型服务配置类型
     */
    public static <T extends LLMService, C extends LLMServiceConfig> void registerLLMService(String serviceName, LLMServiceBuilder<T, C> serviceBuilder) {
        registry.put(serviceName, serviceBuilder);
    }

    /**
     * 创建大模型服务
     *
     * @param serviceName   大模型服务名称
     * @param serviceConfig 大模型服务配置
     * @param <T>           大模型服务类型
     * @param <C>           大模型服务配置类型
     * @return 大模型服务
     */
    @SuppressWarnings("unchecked")
    public static <T extends LLMService, C extends LLMServiceConfig> T createLLMService(String serviceName, C serviceConfig) {
        // 使用serviceName从大模型服务池中筛选构建器
        LLMServiceBuilder<T, C> serviceBuilder = (LLMServiceBuilder<T, C>) registry.get(serviceName);
        if (serviceBuilder == null) {
            throw new IllegalArgumentException("Invalid LLM service name: " + serviceName);
        }
        // 构建器使用大模型服务配置构建服务
        return serviceBuilder.build(serviceConfig);
    }
}
