package com.evalkit.framework.eval.node.dataloader_wrapper;

import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * prompt数据装饰器,可使用大模型对原始输入进行变换
 */
@Slf4j
public abstract class PromptDataLoaderWrapper extends DataLoaderWrapper {
    /* 大模型服务 */
    protected final LLMService llmService;

    public PromptDataLoaderWrapper(LLMService llmService) {
        this(1, llmService);
    }

    public PromptDataLoaderWrapper(int threadNum, LLMService llmService) {
        super(threadNum);
        if (llmService == null) {
            throw new EvalException("llmService is null");
        }
        this.llmService = llmService;
    }

    /**
     * 准备提示词
     */
    public abstract String preparePrompt();

    /**
     * 选择要装饰的字段
     */
    public abstract String selectField();

    @Override
    protected void wrapper(DataItem dataItem) {
        InputData inputData = dataItem.getInputData();
        String field = selectField();
        String value = inputData.get(field, null);
        // 空字段值直接结束
        if (StringUtils.isEmpty(value)) {
            return;
        }
        String prompt = preparePrompt();
        String msg = String.format("%s\n\n输出要求: 直接输出处理后的结果,不要任何解释\n\n输入文本: %s", prompt, value);
        String llmReply = llmService.chat(msg);
        inputData.set(field, llmReply);
        log.info("Finish wrapper dataItem: {}", dataItem);
    }
}
