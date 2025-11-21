package com.evalkit.framework.infra.service.llm.strategy;

import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Random;

/**
 * 随机负载均衡策略
 */
@Slf4j
public class RandomLoadBalanceStrategy implements LoadBalanceStrategy {
    @Override
    public LLMService selectLLMService(List<LLMService> llmServices) {
        if (CollectionUtils.isEmpty(llmServices)) {
            return null;
        }
        int idx = new Random().nextInt(llmServices.size());
        LLMService llmService = llmServices.get(idx);
        log.info("Selected LLM service index: {}, model: {}", idx, llmService.getModel());
        return llmService;
    }
}
