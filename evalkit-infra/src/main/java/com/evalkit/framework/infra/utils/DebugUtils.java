package com.evalkit.framework.infra.utils;

import com.evalkit.framework.common.utils.runtime.RuntimeEnvUtils;
import com.evalkit.framework.infra.service.llm.LLMService;
import com.evalkit.framework.infra.service.llm.LLMServiceFactory;
import com.evalkit.framework.infra.service.llm.config.DeepseekLLMServiceConfig;
import com.evalkit.framework.infra.service.llm.constants.LLMServiceEnum;

public class DebugUtils {
    private DebugUtils() {
    }

    public static LLMService buildLLMService() {
        String deepSeekToken = RuntimeEnvUtils.getPropertyFromResource("secret.properties", "deepseek-token");
        DeepseekLLMServiceConfig config = DeepseekLLMServiceConfig.builder()
                .apiToken(deepSeekToken)
                .inPrice(4)
                .outPrice(3)
                .build();
        return LLMServiceFactory.createLLMService(LLMServiceEnum.DEEPSEEK.name(), config);
    }
}
