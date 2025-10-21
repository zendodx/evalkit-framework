package com.evalkit.framework.eval.node.api.config;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.concurrent.TimeUnit;

@SuperBuilder
@Data
public class ApiCompletionConfig {
    /* 并发调用线程数 */
    @Builder.Default
    protected int threadNum = 1;
    /* 接口超时时间,默认120秒 */
    @Builder.Default
    protected long timeout = 120;
    @Builder.Default
    protected TimeUnit timeUnit = TimeUnit.SECONDS;
}
