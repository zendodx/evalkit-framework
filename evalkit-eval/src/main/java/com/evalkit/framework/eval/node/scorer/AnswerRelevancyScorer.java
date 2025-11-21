package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.common.utils.string.RegexUtils;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.scorer.config.PromptBasedScorerConfig;
import org.apache.commons.lang3.StringUtils;

/**
 * 答案相关性评估器
 */
public abstract class AnswerRelevancyScorer extends PromptBasedScorer {
    public AnswerRelevancyScorer(PromptBasedScorerConfig config) {
        super(config);
    }

    @Override
    public String prepareSysPrompt() {
        return "【角色】你是「AnswerRelevancyScorer」——专门给“答案是否切题”打分的冷面裁判，只关心「答没答到点上」，不关心答案对错、语气好坏、信息真假。" +
                "【任务】阅读用户问题 Q 与候选答案 A，判断 A 是否直接回应了 Q 的核心诉求。 " +
                "请按照要求检查文本，输出结果严格限制为如下json：" +
                "{\t\"score\":\"# 输出格式 一个分数 # 打分要求 - 若 A 完全跑题、答非所问、遗漏关键子问，得 0 分。 " +
                "- 若 A 部分回应但仍缺核心点，得 0.1–0.4 分。" +
                "- 若 A 基本回应但边缘信息冗余或轻微偏离，得 0.5–0.7 分。" +
                "- 若 A 精准、无冗余、无遗漏，得 0.8–1.0 分。\",\"reason\":\"推理过程及解释\"} " +
                "执行完成后你需要对返回结果进行自检查，如果不符合json格式要求，请重新生成。";
    }

    @Override
    public String prepareUserPrompt(InputData inputData, ApiCompletionResult apiCompletionResult) {
        String query = prepareQuery(inputData, apiCompletionResult);
        String answer = prepareAnswer(inputData, apiCompletionResult);
        return String.format("问题: %s\n答案: %s", query, answer);
    }

    public abstract String prepareQuery(InputData inputData, ApiCompletionResult apiCompletionResult);

    public abstract String prepareAnswer(InputData inputData, ApiCompletionResult apiCompletionResult);

    @Override
    public LLMResult parseLLMReply(String reply) {
        String jsonBlock = RegexUtils.extractMarkdownJsonBlock(reply);
        return JsonUtils.fromJson(StringUtils.isEmpty(jsonBlock) ? reply : jsonBlock, LLMResult.class);
    }
}
