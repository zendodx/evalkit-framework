package com.evalkit.framework.infra.service.llm;

/**
 * 大模型服务
 */
public interface LLMService {
    /**
     * 大模型对话
     */
    String chat(String prompt);

    /**
     * 获取模型名称
     */
    String getModel();
}
