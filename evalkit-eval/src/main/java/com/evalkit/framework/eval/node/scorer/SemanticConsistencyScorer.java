package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.common.utils.string.RegexUtils;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.scorer.config.PromptBasedScorerConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 语义一致性评估器
 */
@Slf4j
public abstract class SemanticConsistencyScorer extends PromptBasedScorer {
    public SemanticConsistencyScorer(PromptBasedScorerConfig config) {
        super(config);
    }

    @Override
    public String prepareSysPrompt() {
        return "你是“语义一致性裁判”，判断A、B两句话的语义是否完全一致（即在任何语境下可互相替换而不改变事实、立场、情感、时间、主客体）。" +
                "请按照要求检查文本，输出结果严格限制为如下json：" +
                "{\t\"score\":\"# 输出格式 一个整数 0或者1 # 检查要求 检查文本是否符合一致性规则 # 打分要求 - 0分：不符合 - 1分：符合\",\"reason\":\"推理过程及解释\"} " +
                "执行完成后你需要对返回结果进行自检查，如果不符合json格式要求，请重新生成。";
    }

    @Override
    public String prepareUserPrompt(InputData inputData, ApiCompletionResult apiCompletionResult) {
        String textA = prepareTextA(inputData, apiCompletionResult);
        String textB = prepareTextB(inputData, apiCompletionResult);
        return String.format("句子A: %s\n句子B: %s", textA, textB);
    }

    /**
     * 准备文本A
     *
     * @param inputData           输入数据
     * @param apiCompletionResult API调用结果
     * @return 文本A
     */
    public abstract String prepareTextA(InputData inputData, ApiCompletionResult apiCompletionResult);

    /**
     * 准备文本B
     *
     * @param inputData           输入数据
     * @param apiCompletionResult API调用结果
     * @return 文本B
     */
    public abstract String prepareTextB(InputData inputData, ApiCompletionResult apiCompletionResult);

    @Override
    public LLMResult parseLLMReply(String reply) {
        String jsonBlock = RegexUtils.extractMarkdownJsonBlock(reply);
        return JsonUtils.fromJson(StringUtils.isEmpty(jsonBlock) ? reply : jsonBlock, LLMResult.class);
    }
}
