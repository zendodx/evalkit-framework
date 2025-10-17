package com.evalkit.framework.infra.service.llm.config;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
public class DeepseekLLMServiceConfig extends LLMServiceConfig {
    // deepseek接口访问密钥
    protected String apiToken;
    // 默认使用的deepseek模型名称
    @Builder.Default
    protected String model = "deepseek-chat";
}
