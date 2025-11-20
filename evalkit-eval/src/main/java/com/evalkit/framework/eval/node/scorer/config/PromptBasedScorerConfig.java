package com.evalkit.framework.eval.node.scorer.config;

import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.concurrent.TimeUnit;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class PromptBasedScorerConfig extends ScorerConfig {
    /* 大模型服务 */
    protected LLMService llmService;
    /* SysPrompt */
    protected String sysPrompt;
    /* 是否开启失败重试 */
    @Builder.Default
    protected boolean enableRetry = true;
    /* 重试间隔, 默认10秒 */
    @Builder.Default
    protected long retryInterval = 10;
    /* 重试时间单位 */
    @Builder.Default
    protected TimeUnit retryTimeUnit = TimeUnit.SECONDS;
    /* 最大重试次数, 默认6次 */
    @Builder.Default
    protected int retryTimes = 6;
}
