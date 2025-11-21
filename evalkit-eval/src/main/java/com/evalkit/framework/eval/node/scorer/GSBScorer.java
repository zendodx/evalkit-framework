package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.common.utils.string.RegexUtils;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.scorer.config.PromptBasedScorerConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * GSB模型打分器
 * G:效果提升
 * S:效果布标
 * B:效果降低
 */
@Slf4j
public abstract class GSBScorer extends PromptBasedScorer {
    protected static final String DEFAULT_SYS_PROMPT = "你是一位公正的评估专家。请根据以下标准，对候选答案进行打分，并给出理由。\n" +
            "\n" +
            "请按以下维度打分（1~5 分，5 为最好）：\n" +
            "- 准确性：是否事实正确\n" +
            "- 相关性：是否回答问题\n" +
            "- 完整性：是否遗漏关键信息\n" +
            "- 流畅性：语言是否自然\n" +
            "\n" +
            "请按 JSON 格式输出：\n" +
            "{\n" +
            "  \"accuracy\": 5,\n" +
            "  \"relevance\": 5,\n" +
            "  \"completeness\": 5,\n" +
            "  \"fluency\": 5,\n" +
            "  \"reason\": \"评判理由，例如：候选答案与金标准语义一致，语言自然，无遗漏。\"\n" +
            "}";

    public GSBScorer(PromptBasedScorerConfig config) {
        super(config);
    }

    /**
     * GSB打分结果
     */
    @Data
    protected static class GSBScore {
        protected double accuracy;
        protected double relevance;
        protected double completeness;
        protected double fluency;
        protected String reason;

        public double calAvgScore() {
            return (accuracy + relevance + completeness + fluency) / 4.0;
        }

        public double calScoreRate() {
            return calAvgScore() / 5.0;
        }
    }

    @Override
    public String prepareSysPrompt() {
        if (StringUtils.isNotEmpty(config.getSysPrompt())) {
            return config.getSysPrompt();
        }
        return DEFAULT_SYS_PROMPT;
    }

    @Override
    public String prepareUserPrompt(InputData inputData, ApiCompletionResult apiCompletionResult) {
        String userPrompt = "【输入问题】：\n" +
                "{input}\n" +
                "\n" +
                "【金标准答案】：\n" +
                "{gold}\n" +
                "\n" +
                "【候选答案】：\n" +
                "{candidate}\n";
        String gold = prepareGoldAnswer(inputData, apiCompletionResult);
        String candidate = prepareCandidateAnswer(inputData, apiCompletionResult);
        String input = prepareInput(inputData, apiCompletionResult);
        userPrompt = StringUtils.replace(userPrompt, "{gold}", gold);
        userPrompt = StringUtils.replace(userPrompt, "{candidate}", candidate);
        userPrompt = StringUtils.replace(userPrompt, "{input}", input);
        return userPrompt;
    }

    public abstract String prepareGoldAnswer(InputData inputData, ApiCompletionResult apiCompletionResult);

    public abstract String prepareCandidateAnswer(InputData inputData, ApiCompletionResult apiCompletionResult);

    public abstract String prepareInput(InputData inputData, ApiCompletionResult apiCompletionResult);

    @Override
    public LLMResult parseLLMReply(String reply) {
        String jsonBlock = RegexUtils.extractMarkdownJsonBlock(reply);
        GSBScore gsbScore = JsonUtils.fromJson(jsonBlock, GSBScore.class);
        double scoreRate = gsbScore.calScoreRate();
        LLMResult llmResult = new LLMResult();
        llmResult.setReason(gsbScore.getReason());
        llmResult.setScore(scoreRate);
        return llmResult;
    }
}
