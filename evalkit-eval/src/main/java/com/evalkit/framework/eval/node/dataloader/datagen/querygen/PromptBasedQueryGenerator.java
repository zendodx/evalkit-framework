package com.evalkit.framework.eval.node.dataloader.datagen.querygen;

import com.evalkit.framework.eval.node.dataloader.datagen.querygen.config.PromptBasedQueryGeneratorConfig;
import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于prompt的Query生成器
 */
@Getter
@Slf4j
public class PromptBasedQueryGenerator implements QueryGenerator {
    protected final PromptBasedQueryGeneratorConfig config;

    public PromptBasedQueryGenerator(PromptBasedQueryGeneratorConfig config) {
        this.config = config;
    }

    @Override
    public List<String> generate() {
        String sysPrompt = config.getSysPrompt();
        String userPrompt = config.getUserPrompt();
        String langStyle = config.getLangStyle();
        int genCount = config.getGenCount();
        LLMService llmService = config.getLlmService();
        if (llmService == null) {
            throw new IllegalArgumentException("LLMService is null");
        }
        String prompt = sysPrompt + "\n" +
                "语言风格:" + langStyle + "\n" +
                "数量要求: 严格限制输出" + genCount + "条\n" +
                userPrompt;
        String chat = llmService.chat(prompt);
        log.info("Generate query prompt: {}, reply: {}", prompt, chat);
        String[] split = StringUtils.split(chat, "\n");
        return Arrays.stream(split).filter(StringUtils::isNotBlank).collect(Collectors.toList());
    }
}
