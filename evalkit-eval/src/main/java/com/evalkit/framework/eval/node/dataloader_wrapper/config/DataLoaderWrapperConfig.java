package com.evalkit.framework.eval.node.dataloader_wrapper.config;

import com.evalkit.framework.infra.service.llm.LLMService;
import com.evalkit.framework.workflow.model.NodeConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
public class DataLoaderWrapperConfig extends NodeConfig {
    /* 大模型服务 */
    private LLMService llmService;
}
