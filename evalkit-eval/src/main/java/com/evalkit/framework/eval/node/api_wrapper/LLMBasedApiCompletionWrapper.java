package com.evalkit.framework.eval.node.api_wrapper;

import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.node.api_wrapper.config.LLMBasedApiCompletionConfig;
import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 基于大模型的api调用结果装饰器
 * <p>
 * 使用大模型对接口返回结果进行转化，将原始输出转换为符合评估要求的格式
 */
@Slf4j
public abstract class LLMBasedApiCompletionWrapper extends ApiCompletionWrapper {

    protected final LLMBasedApiCompletionConfig llmConfig;

    public LLMBasedApiCompletionWrapper(LLMBasedApiCompletionConfig config) {
        super(config);
        this.llmConfig = config;
    }

    /**
     * 准备提示词
     *
     * @param dataItem 数据项（包含输入数据和接口调用结果）
     * @return 发送给大模型的提示词
     */
    public abstract String preparePrompt(DataItem dataItem);

    /**
     * 将大模型输出写回 ApiCompletionResult
     *
     * @param result    原始接口调用结果
     * @param llmOutput 大模型转化后的输出
     */
    public abstract void applyLLMOutput(ApiCompletionResult result, String llmOutput);

    @Override
    protected void wrapper(DataItem dataItem) {
        ApiCompletionResult result = dataItem.getApiCompletionResult();
        if (result == null) {
            log.warn("ApiCompletionResult is null, skip wrapper, dataItem: {}", dataItem);
            return;
        }
        String prompt = preparePrompt(dataItem);
        if (StringUtils.isEmpty(prompt)) {
            log.warn("Prompt is empty, skip wrapper, dataItem: {}", dataItem);
            return;
        }
        LLMService llmService = llmConfig.getLlmService();
        String llmOutput = llmService.chat(prompt);
        applyLLMOutput(result, llmOutput);
        log.info("Finish LLM-based wrapper, dataItem: {}", dataItem);
    }
}
