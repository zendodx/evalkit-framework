package com.evalkit.framework.eval.node.api_wrapper.config;

import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
public class LLMBasedApiCompletionConfig extends ApiCompletionWrapperConfig {
    /* 大模型服务（基于大模型的装饰器使用） */
    private LLMService llmService;
}
