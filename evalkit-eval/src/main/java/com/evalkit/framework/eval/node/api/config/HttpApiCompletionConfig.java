package com.evalkit.framework.eval.node.api.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
public class HttpApiCompletionConfig extends ApiCompletionConfig {
    private String host;
    private String api;
    private String method;
}
