package com.evalkit.framework.eval.node.counter.config;

import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.Builder;
import lombok.Data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 归因 Counter V2 配置
 */
@Data
@Builder
public class AttributeCounterV2Config {

    /**
     * 内置默认标准大类
     */
    public static final List<String> DEFAULT_CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            "回答准确性", "回答完整性", "格式规范性", "语言表达",
            "业务知识", "逻辑推理", "指令遵循", "安全合规", "其他"
    ));

    /* LLM 服务，必填 */
    private LLMService llmService;

    /**
     * 标准大类列表，用于约束 LLM 提取时的分类范围。
     * 默认使用内置的 9 个通用类别，可完全替换或在 DEFAULT_CATEGORIES 基础上追加。
     */
    @Builder.Default
    private List<String> standardCategories = DEFAULT_CATEGORIES;
}

