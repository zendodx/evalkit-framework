package com.evalkit.framework.eval.node.dataloader.config;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.concurrent.TimeUnit;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
public class ApiDataLoaderConfig extends DataLoaderConfig {
    /* 请求地址 */
    private String host;
    /* 请求api */
    private String api;
    /* 请求方法 */
    private String method;
    /* 超时时间, 默认120秒 */
    @Builder.Default
    private long timeout = 120;
    @Builder.Default
    private TimeUnit timeUnit = TimeUnit.SECONDS;
}
