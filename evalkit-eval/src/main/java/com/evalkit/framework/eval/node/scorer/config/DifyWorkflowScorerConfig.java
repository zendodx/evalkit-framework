package com.evalkit.framework.eval.node.scorer.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * Dify工作流评估器配置
 */
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
public class DifyWorkflowScorerConfig extends ScorerConfig {
    private String apiKey;
    private String userName;
    private String baseUrl;
}
