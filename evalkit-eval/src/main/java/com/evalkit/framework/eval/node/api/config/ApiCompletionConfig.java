package com.evalkit.framework.eval.node.api.config;

import com.evalkit.framework.workflow.model.NodeConfig;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.concurrent.TimeUnit;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
public class ApiCompletionConfig extends NodeConfig {
    /* 接口超时时间,默认120秒 */
    @Builder.Default
    protected long timeout = 120;
    @Builder.Default
    protected TimeUnit timeUnit = TimeUnit.SECONDS;
}
