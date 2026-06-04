package com.evalkit.framework.eval.node.api_wrapper.config;

import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * api调用结果装饰器配置
 */
@SuperBuilder
@Data
public class ApiCompletionWrapperConfig {
    /* 并发调用线程数 */
    @Builder.Default
    private int threadNum = 1;
}
