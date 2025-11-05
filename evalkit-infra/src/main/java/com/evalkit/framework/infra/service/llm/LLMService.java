package com.evalkit.framework.infra.service.llm;

/**
 * 大模型服务统一接口
 */
public interface LLMService {
    /**
     * 大模型对话
     *
     * @param prompt 提示词
     * @return 模型回复
     */
    String chat(String prompt);

    /**
     * 获取模型名称
     *
     * @return 模型名称
     */
    String getModel();
}
