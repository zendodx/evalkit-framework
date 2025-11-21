package com.evalkit.framework.infra.service.llm;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.infra.service.llm.config.LoadBalanceLLMServiceConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于负载均衡的LLM服务
 */
@Slf4j
public class LoadBalanceLLMService implements LLMService {
    protected final LoadBalanceLLMServiceConfig config;

    public LoadBalanceLLMService(LoadBalanceLLMServiceConfig config) {
        validConfig(config);
        this.config = config;
    }

    protected void validConfig(LoadBalanceLLMServiceConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("LoadBalanceLLMServiceConfig is null");
        }
        if (CollectionUtils.isEmpty(config.getLlmServices())) {
            throw new IllegalArgumentException("llmServices is empty");
        }
        if (config.getLoadBalanceStrategy() == null) {
            throw new IllegalArgumentException("loadBalanceStrategy is null");
        }
    }

    @Override
    public String chat(String prompt) {
        List<LLMService> llmServices = config.getLlmServices();
        LLMService service = config.getLoadBalanceStrategy().selectLLMService(llmServices);
        return service.chat(prompt);
    }

    @Override
    public String getModel() {
        List<LLMService> llmServices = config.getLlmServices();
        Set<String> models = new LinkedHashSet<>();
        for (LLMService service : llmServices) {
            String curModel = service.getModel();
            models.add(curModel);
        }
        return JsonUtils.toJson(models);
    }
}
