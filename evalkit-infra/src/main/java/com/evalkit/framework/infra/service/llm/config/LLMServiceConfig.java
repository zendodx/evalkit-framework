package com.evalkit.framework.infra.service.llm.config;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * 大模型服务基础配置
 */
@SuperBuilder
@Data
public class LLMServiceConfig {
    // 模型名称
    protected String model;
    // 最大token
    @Builder.Default
    protected long maxTokens = 4068;
}
