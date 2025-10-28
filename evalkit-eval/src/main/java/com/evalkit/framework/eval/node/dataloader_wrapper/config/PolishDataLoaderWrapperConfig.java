package com.evalkit.framework.eval.node.dataloader_wrapper.config;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
public class PolishDataLoaderWrapperConfig extends DataLoaderWrapperConfig {
    /* 默认润色prompt */
    @Builder.Default
    protected String sysPrompt = "你是一个中文自然语言改写助手。  \n" +
            "我会给你一个用户的原始查询（query），你的任务是将它改写成更符合日常口语化的表达方式，保持原句的核心意图和语义不变。  \n" +
            "\n" +
            "改写要求：\n" +
            "1. 保留原句的核心信息和意图，不得新增或删除关键需求。\n" +
            "2. 可以调整语序、用词，使其更自然、更贴近真实用户的说话习惯。\n" +
            "3. 可以适当补充合理的场景或人物画像（如身份、背景、情绪），但不得改变原句的含义。\n" +
            "4. 输出一个改写后的句子，不要解释改写过程。\n" +
            "\n" +
            "示例：\n" +
            "原句：给我订张明天从北京到上海的高铁票。\n" +
            "改写：我准备明天去上海出差，帮我订一张从北京出发的高铁票。\n" +
            "\n" +
            "原句：帮我查一下下周五去广州的动车。\n" +
            "改写：我打算下周五去广州旅游，麻烦帮我查一下动车票。";
    /* 语言风格 */
    @Builder.Default
    protected String style = "清晰明确";
    /* 文本分隔符 */
    protected String splitChar;
}
