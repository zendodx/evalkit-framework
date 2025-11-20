package com.evalkit.framework.eval.node.scorer.config;

import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class PromptBasedScorerConfig extends ScorerConfig {
    /* 大模型服务 */
    protected LLMService llmService;
}
