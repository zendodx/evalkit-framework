package com.evalkit.framework.eval.node.dataloader_wrapper.config;

import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class PolishDataLoaderWrapperConfig {
    /* 默认润色prompt */
    @Builder.Default
    protected String sysPrompt = "【人设】你是我最好的朋友，平时说话自然、有趣，不装。\n【任务】 把下面这段对话帮我“顺一顺”：(1)去掉啰嗦、重复和尴尬词；(2)让语气更顺口、更像真人聊天；(3)可以偶尔加个小表情或语气词，但别太油。";
    /* 语言风格 */
    @Builder.Default
    protected String style = "清晰明确";
    /* 文本分隔符 */
    protected String splitChar;
    /* 大模型服务 */
    protected LLMService llmService;
    /* 并发数 */
    @Builder.Default
    protected int threadNum = 1;
}
