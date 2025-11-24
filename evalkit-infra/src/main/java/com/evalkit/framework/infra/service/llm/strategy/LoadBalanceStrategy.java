package com.evalkit.framework.infra.service.llm.strategy;

import com.evalkit.framework.infra.service.llm.LLMService;

import java.util.List;

/**
 * 负载均衡策略
 */
public interface LoadBalanceStrategy {
    /**
     * 选择LLM服务
     *
     * @param llmServices LLM服务列表
     * @return 选中的LLM服务
     */
    LLMService selectLLMService(List<LLMService> llmServices);
}
