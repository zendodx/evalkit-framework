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

    /**
     * 每个 chunk 最大 token 数（给 8k 模型预留余量）。
     * 默认 6000。
     */
    @Builder.Default
    private int maxTokensPerChunk = 6000;

    /**
     * 为每条 issue 生成代表摘要时，最多取几条样本描述。
     * 默认 5。
     */
    @Builder.Default
    private int summarySampleSize = 5;

    /**
     * 生成代表摘要的最大字数限制（写入 Prompt）。
     * 默认 50。
     */
    @Builder.Default
    private int summaryMaxChars = 50;

    /**
     * 提取 / 生成摘要时的最大并行度（受控线程池大小）。
     * 默认 CPU 核数 * 4。
     */
    @Builder.Default
    private int parallelism = Runtime.getRuntime().availableProcessors() * 4;

    /**
     * 当传入了标准枚举类别时，是否跳过 normalizeCategories 的 LLM 调用。
     * <ul>
     *   <li>{@code true}（默认）：提取阶段已约束了枚举，category 名称本就一致，无需再归一化，节省一次 LLM 调用。</li>
     *   <li>{@code false}：强制执行归一化，适合未使用枚举约束或多模型混合归因的场景。</li>
     * </ul>
     */
    @Builder.Default
    private boolean skipNormalizeCategoriesIfEnumConstrained = true;
}

