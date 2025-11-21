package com.evalkit.framework.infra.service.llm.config;

import com.evalkit.framework.infra.service.llm.LLMService;
import com.evalkit.framework.infra.service.llm.strategy.LoadBalanceStrategy;
import com.evalkit.framework.infra.service.llm.strategy.RandomLoadBalanceStrategy;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class LoadBalanceLLMServiceConfig {
    protected List<LLMService> llmServices;
    @Builder.Default
    protected LoadBalanceStrategy loadBalanceStrategy = new RandomLoadBalanceStrategy();
}
