package com.evalkit.framework.infra.service.llm.strategy;

import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询负载均衡策略
 */
@Slf4j
public class RoundRobinLoadBalanceStrategy implements LoadBalanceStrategy {
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public LLMService selectLLMService(List<LLMService> llmServices) {
        if (CollectionUtils.isEmpty(llmServices)) {
            return null;
        }
        // 顺序轮询：counter 自增后取模
        int idx = counter.getAndIncrement() % llmServices.size();
        LLMService llmService = llmServices.get(idx);
        log.info("Selected LLM service index: {}, model: {}", idx, llmService.getModel());
        return llmService;
    }
}
