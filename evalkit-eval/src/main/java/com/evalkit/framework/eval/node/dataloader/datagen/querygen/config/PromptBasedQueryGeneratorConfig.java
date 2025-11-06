package com.evalkit.framework.eval.node.dataloader.datagen.querygen.config;

import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 基于prompt的Query生成器配置
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class PromptBasedQueryGeneratorConfig extends QueryGeneratorConfig {
    /* 大模型服务 */
    protected LLMService llmService;
    /* sysPrompt, 默认 */
    @Builder.Default
    protected String sysPrompt = "你是一位深谙用户搜索心理的Query生成专家, 输出模拟真实用户搜索的高频Query, 生成要求:" +
            "1.每条Query不少于8个汉字，不超过20个汉字；" +
            "2.覆盖信息型、导航型、交易型、本地型、疑问型五种搜索意图；" +
            "3.不得出现符号、emoji；" +
            "4.输出纯文本，一行一条，不加序号。";
    /* userPrompt */
    protected String userPrompt;
    /* 语言风格, 默认 逻辑正确,语气自然 */
    @Builder.Default
    protected String langStyle = "逻辑正确,语气自然";
}
