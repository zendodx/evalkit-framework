package com.evalkit.framework.infra.service.llm.config;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * DeepSeek大模型服务配置
 */
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
public class DeepseekLLMServiceConfig extends LLMServiceConfig {
    // DeepSeek接口访问密钥
    protected String apiToken;
    // DeepSeek大模型名称, 默认deepseek-chat
    @Builder.Default
    protected String model = "deepseek-chat";
}
