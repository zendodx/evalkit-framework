package com.evalkit.framework.eval.node.dataloader_wrapper.config;

import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class DataLoaderWrapperConfig {
    @Builder.Default
    private int threadNum = 1;
    /* 大模型服务 */
    private LLMService llmService;
}
