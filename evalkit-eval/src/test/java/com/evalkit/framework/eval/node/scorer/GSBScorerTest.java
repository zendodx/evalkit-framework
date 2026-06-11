package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.scorer.config.PromptBasedScorerConfig;
import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
class GSBScorerTest {
    LLMService llmService;

    @BeforeEach
    void setUp() {
        // 使用 mock LLMService 替代真实 DeepSeek，不依赖外部 token 或 HTTP 请求
        // GSBScorer.parseLLMReply 期望 LLM 返回 JSON 格式，包含 accuracy/relevance/completeness/fluency/reason 字段
        llmService = new LLMService() {
            @Override
            public String chat(String prompt) {
                // 返回符合 GSBScorer 期望的 JSON 格式（各维度低分，表示候选回答较差）
                return "{\n" +
                        "  \"accuracy\": 2,\n" +
                        "  \"relevance\": 2,\n" +
                        "  \"completeness\": 2,\n" +
                        "  \"fluency\": 3,\n" +
                        "  \"reason\": \"候选答案与金标准存在明显差距，缺少关键信息。\"\n" +
                        "}";
            }

            @Override
            public String getModel() {
                return "mock-model";
            }
        };
    }

    @Test
    void test() {
        GSBScorer gsbScorer = new GSBScorer(
                PromptBasedScorerConfig.builder()
                        .llmService(llmService)
                        .build()
        ) {
            @Override
            public String prepareGoldAnswer(InputData inputData, ApiCompletionResult apiCompletionResult) {
                return "乔布斯是美国人";
            }

            @Override
            public String prepareCandidateAnswer(InputData inputData, ApiCompletionResult apiCompletionResult) {
                return "乔布美国人";
            }

            @Override
            public String prepareInput(InputData inputData, ApiCompletionResult apiCompletionResult) {
                return "乔布斯是非洲人";
            }
        };
        DataItem dataItem = new DataItem();
        dataItem.setInputData(new InputData());
        dataItem.setApiCompletionResult(new ApiCompletionResult());
        ScorerResult scorerResult = gsbScorer.eval(dataItem);

        assertNotNull(scorerResult, "评分结果不应为 null");
        log.info("scorerResult:{}", scorerResult);
    }

    @Test
    void testGoodResult() {
        // mock LLM 返回高分 JSON，表示候选回答比参考回答好
        // GSBScorer.parseLLMReply 期望 JSON 格式，包含 accuracy/relevance/completeness/fluency/reason
        LLMService goodLLM = new LLMService() {
            @Override
            public String chat(String prompt) {
                return "{\n" +
                        "  \"accuracy\": 5,\n" +
                        "  \"relevance\": 5,\n" +
                        "  \"completeness\": 5,\n" +
                        "  \"fluency\": 5,\n" +
                        "  \"reason\": \"候选答案与金标准语义一致，语言自然，无遗漏。\"\n" +
                        "}";
            }

            @Override
            public String getModel() {
                return "mock-model";
            }
        };

        GSBScorer gsbScorer = new GSBScorer(
                PromptBasedScorerConfig.builder()
                        .llmService(goodLLM)
                        .build()
        ) {
            @Override
            public String prepareGoldAnswer(InputData inputData, ApiCompletionResult apiCompletionResult) {
                return "gold answer";
            }

            @Override
            public String prepareCandidateAnswer(InputData inputData, ApiCompletionResult apiCompletionResult) {
                return "better candidate";
            }

            @Override
            public String prepareInput(InputData inputData, ApiCompletionResult apiCompletionResult) {
                return "test input";
            }
        };

        DataItem dataItem = new DataItem();
        dataItem.setInputData(new InputData());
        dataItem.setApiCompletionResult(new ApiCompletionResult());
        ScorerResult result = gsbScorer.eval(dataItem);

        assertNotNull(result, "评分结果不应为 null");
        log.info("Good result scorerResult:{}", result);
    }
}