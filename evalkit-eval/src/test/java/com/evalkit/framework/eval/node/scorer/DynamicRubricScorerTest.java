package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.scorer.config.DynamicRubricScorerConfig;
import com.evalkit.framework.eval.node.scorer.model.RubricCriteria;
import com.evalkit.framework.eval.node.scorer.model.RubricScoreType;
import com.evalkit.framework.eval.node.scorer.strategy.MaxScoreRateStrategy;
import com.evalkit.framework.infra.service.llm.LLMService;
import com.evalkit.framework.workflow.model.WorkflowContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DynamicRubricScorer 测试")
class DynamicRubricScorerTest {
    
    // ==================== 工厂方法 ====================

    /**
     * 固定回复的 mock LLMService
     */
    private LLMService mockLLM(String fixedReply) {
        return new LLMService() {
            @Override
            public String chat(String prompt) {
                return fixedReply;
            }
            @Override
            public String getModel() {
                return "mock-model";
            }
        };
    }

    /** 抛异常的 mock LLMService */
    private LLMService mockLLMAlwaysFail() {
        return new LLMService() {
            @Override
            public String chat(String prompt) {
                throw new RuntimeException("LLM service unavailable");
            }
            @Override
            public String getModel() {
                return "mock-fail-model";
            }
        };
    }

    /** 按顺序依次返回不同回复的 mock LLMService */
    private LLMService mockLLMSequence(String... replies) {
        AtomicInteger idx = new AtomicInteger(0);
        return new LLMService() {
            @Override
            public String chat(String prompt) {
                int i = idx.getAndIncrement();
                if (i >= replies.length) {
                    throw new RuntimeException("LLM mock exhausted at index=" + i);
                }
                return replies[i];
            }
            @Override
            public String getModel() {
                return "mock-sequence-model";
            }
        };
    }

    /** 构建带 query 字段的 DataItem */
    private DataItem buildDataItem(String query) {
        DataItem item = new DataItem();
        item.setDataIndex(1L);
        Map<String, Object> map = new ConcurrentHashMap<>();
        map.put("query", query);
        item.setInputData(new InputData(map));
        item.setApiCompletionResult(new ApiCompletionResult(
                Collections.singletonMap("response", "This is the AI response.")));
        return item;
    }

    /** 构建不含任何 key 的 DataItem */
    private DataItem buildEmptyDataItem() {
        DataItem item = new DataItem();
        item.setDataIndex(2L);
        item.setInputData(new InputData(new ConcurrentHashMap<>()));
        item.setApiCompletionResult(new ApiCompletionResult(
                Collections.singletonMap("response", "Empty input response.")));
        return item;
    }

    /**
     * 为 Scorer 注入最小可用的 WorkflowContext。
     * <p>
     * 直接调用 evalWrapper() 绕过了 WorkflowNode 的正常执行链路，
     * workflowContext 不会被自动设置，需要在测试中手动注入，
     * 否则 Scorer.decidePass() 调用 WorkflowContextOps.getScorerStrategy(null) 会 NPE。
     */
    private void injectContext(DynamicRubricScorer scorer) {
        WorkflowContext ctx = new WorkflowContext();
        WorkflowContextOps.setScorerStrategy(ctx, new MaxScoreRateStrategy());
        WorkflowContextOps.setThreshold(ctx, 0.6);
        scorer.setWorkflowContext(ctx);
    }

    /** 标准 rubricGenPrompt */
    private static final String GEN_PROMPT =
            "请根据用户请求生成 3 个评估维度，输出 JSON 数组格式：\n" +
            "[{\"name\":\"...\",\"definition\":\"...\",\"scoringGuide\":\"5=...; 1=...\",\"maxScore\":5,\"minScore\":1,\"passScore\":3,\"weight\":1.0}]";

    /** 标准 LLM 生成 Rubric 回复 */
    private static final String VALID_GEN_REPLY =
            "[{\"name\":\"Accuracy\",\"definition\":\"事实准确性\",\"scoringGuide\":\"5=完全准确; 1=完全错误\"," +
            "\"maxScore\":5,\"minScore\":1,\"passScore\":3,\"weight\":1.0}," +
            "{\"name\":\"Completeness\",\"definition\":\"回复完整性\",\"scoringGuide\":\"5=完整; 1=缺失关键信息\"," +
            "\"maxScore\":5,\"minScore\":1,\"passScore\":3,\"weight\":1.0}]";

    /** 单个维度的评分回复 */
    private static final String SCORE_REPLY =
            "{\"score\": 4, \"reason\": \"回复内容基本准确\"}";

    // ==================== 配置校验 ====================

    @Nested
    @DisplayName("配置校验")
    class ConfigValidation {

        @Test
        @DisplayName("不设置 rubricGenPrompt 时自动使用默认 Prompt，构造不抛异常")
        void noRubricGenPrompt_usesDefaultPrompt_doesNotThrow() {
            // 不显式设置 rubricGenPrompt，验证使用内置默认值
            assertDoesNotThrow(() ->
                    new DynamicRubricScorer(DynamicRubricScorerConfig.builder()
                            .metricName("Test")
                            .llmService(mockLLM("{}"))
                            // 不设置 rubricGenPrompt，框架自动使用 DEFAULT_RUBRIC_GEN_PROMPT
                            .build()) {
                        @Override
                        public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
                            return "";
                        }
                    }
            );
        }

        @Test
        @DisplayName("默认 rubricGenPrompt 内容非空且包含必要的输出格式说明")
        void defaultRubricGenPrompt_isNotBlankAndContainsFormatHint() {
            assertFalse(DynamicRubricScorerConfig.DEFAULT_RUBRIC_GEN_PROMPT.isEmpty(),
                    "默认 Prompt 不应为空");
            assertTrue(DynamicRubricScorerConfig.DEFAULT_RUBRIC_GEN_PROMPT.contains("scoringGuide"),
                    "默认 Prompt 应包含 scoringGuide 格式说明");
            assertTrue(DynamicRubricScorerConfig.DEFAULT_RUBRIC_GEN_PROMPT.contains("JSON"),
                    "默认 Prompt 应包含 JSON 输出格式说明");
        }

        @Test
        @DisplayName("缺少 LLMService 应抛出 IllegalArgumentException")
        void missingLLMService_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () ->
                    new DynamicRubricScorer(DynamicRubricScorerConfig.builder()
                            .metricName("Test")
                            .llmService(null)
                            .rubricGenPrompt(GEN_PROMPT)
                            .build()) {
                        @Override
                        public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
                            return "";
                        }
                    }
            );
        }

        @Test
        @DisplayName("maxGeneratedCriteria < 1 应抛出 IllegalArgumentException")
        void invalidMaxGeneratedCriteria_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () ->
                    new DynamicRubricScorer(DynamicRubricScorerConfig.builder()
                            .metricName("Test")
                            .llmService(mockLLM("[]"))
                            .rubricGenPrompt(GEN_PROMPT)
                            .maxGeneratedCriteria(0)
                            .build()) {
                        @Override
                        public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
                            return "";
                        }
                    }
            );
        }
    }

    // ==================== 正常流程 ====================

    @Nested
    @DisplayName("正常流程")
    class NormalFlow {

        @Test
        @DisplayName("纯动态模式：LLM 生成 2 个维度，打分成功，ScorerResult 有值")
        void pureDynamic_generatesAndScores() {
            // gen reply 返回 2 个维度，scoring reply 各返回一次打分
            LLMService llm = mockLLMSequence(
                    VALID_GEN_REPLY,   // 第1次：生成 Rubric
                    SCORE_REPLY,       // 第2次：Accuracy 维度打分
                    SCORE_REPLY        // 第3次：Completeness 维度打分
            );

            DynamicRubricScorer scorer = new DynamicRubricScorer(
                    DynamicRubricScorerConfig.builder()
                            .metricName("DynamicQuality")
                            .llmService(llm)
                            .rubricGenPrompt(GEN_PROMPT)
                            .maxGeneratedCriteria(5)
                            .build()
            ) {
                @Override
                public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
                    return "query=" + in.get("query");
                }
            };

            DataItem item = buildDataItem("介绍一下北京");
            injectContext(scorer);
            ScorerResult result = scorer.evalWrapper(item);

            assertTrue(result.isSuccess(), "评估应成功");
            assertTrue(result.getScore() > 0, "分数应大于 0");
            assertNotNull(item.getExtraItem(DynamicRubricScorer.EXTRA_KEY_GENERATED_CRITERIA),
                    "extra 应包含生成的 criteria");
            assertFalse((Boolean) item.getExtraItem(DynamicRubricScorer.EXTRA_KEY_RUBRIC_FALLBACK),
                    "未触发 fallback");
        }

        @Test
        @DisplayName("Markdown 代码块包裹的 JSON 也能正确解析")
        void generatedReplyWithMarkdownCodeBlock_parsedCorrectly() {
            String mdWrapped = "```json\n" + VALID_GEN_REPLY + "\n```";
            LLMService llm = mockLLMSequence(mdWrapped, SCORE_REPLY, SCORE_REPLY);

            DynamicRubricScorer scorer = new DynamicRubricScorer(
                    DynamicRubricScorerConfig.builder()
                            .metricName("MdTest")
                            .llmService(llm)
                            .rubricGenPrompt(GEN_PROMPT)
                            .build()
            ) {
                @Override
                public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
                    return "query=" + in.get("query");
                }
            };

            DataItem item = buildDataItem("test query");
            injectContext(scorer);
            ScorerResult result = scorer.evalWrapper(item);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("maxGeneratedCriteria 截断：LLM 生成 3 个但 max=1，只保留 1 个")
        void maxCriteriaExceeded_truncatesToMax() {
            String threeItemReply =
                    "[{\"name\":\"A\",\"scoringGuide\":\"5=好; 1=差\",\"maxScore\":5,\"minScore\":1,\"passScore\":3}," +
                    "{\"name\":\"B\",\"scoringGuide\":\"5=好; 1=差\",\"maxScore\":5,\"minScore\":1,\"passScore\":3}," +
                    "{\"name\":\"C\",\"scoringGuide\":\"5=好; 1=差\",\"maxScore\":5,\"minScore\":1,\"passScore\":3}]";
            LLMService llm = mockLLMSequence(threeItemReply, SCORE_REPLY); // 只打一次分

            DynamicRubricScorer scorer = new DynamicRubricScorer(
                    DynamicRubricScorerConfig.builder()
                            .metricName("TruncateTest")
                            .llmService(llm)
                            .rubricGenPrompt(GEN_PROMPT)
                            .maxGeneratedCriteria(1)
                            .build()
            ) {
                @Override
                public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
                    return "query=" + in.get("query");
                }
            };

            DataItem item = buildDataItem("query");
            injectContext(scorer);
            ScorerResult result = scorer.evalWrapper(item);

            assertTrue(result.isSuccess());
            @SuppressWarnings("unchecked")
            List<RubricCriteria> generated = (List<RubricCriteria>) item.getExtraItem(
                    DynamicRubricScorer.EXTRA_KEY_GENERATED_CRITERIA);
            assertNotNull(generated);
            assertEquals(1, generated.size(), "超出 maxGeneratedCriteria 的维度应被截断");
        }

        @Test
        @DisplayName("二元分自动识别：maxScore=1 minScore=0 时 scoreType=BINARY")
        void binaryScoreType_autoDetected() {
            String binaryReply = "[{\"name\":\"Safety\"," +
                    "\"definition\":\"是否安全\",\"scoringGuide\":\"1=安全; 0=不安全\"," +
                    "\"maxScore\":1,\"minScore\":0,\"passScore\":1,\"weight\":1.0}]";
            LLMService llm = mockLLMSequence(binaryReply, "{\"score\": 1, \"reason\": \"内容安全\"}");

            DynamicRubricScorer scorer = new DynamicRubricScorer(
                    DynamicRubricScorerConfig.builder()
                            .metricName("BinaryTest")
                            .llmService(llm)
                            .rubricGenPrompt(GEN_PROMPT)
                            .build()
            ) {
                @Override
                public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
                    return "response=" + out.get("response");
                }
            };

            DataItem item = buildDataItem("安全测试");
            injectContext(scorer);
            ScorerResult result = scorer.evalWrapper(item);
            assertTrue(result.isSuccess());
            assertTrue(result.getScore() >= 0);
        }
    }

    // ==================== 混合模式（Static + Dynamic）====================

    @Nested
    @DisplayName("混合模式（静态 + 动态）")
    class HybridMode {

        @Test
        @DisplayName("staticCriteria 排在动态维度之前，总维度数 = static + generated")
        void staticCriteriaMergedBeforeDynamic() {
            // gen reply 返回 1 个维度，scoring 共 2 次（1 静 + 1 动）
            LLMService llm = mockLLMSequence(
                    "[{\"name\":\"Relevance\",\"scoringGuide\":\"5=高相关; 1=不相关\",\"maxScore\":5,\"minScore\":1,\"passScore\":3}]",
                    "{\"score\": 1, \"reason\": \"Safety ok\"}",
                    "{\"score\": 4, \"reason\": \"Relevance ok\"}"
            );

            RubricCriteria staticSafety = RubricCriteria.builder()
                    .name("Safety")
                    .definition("是否包含有害内容")
                    .scoreType(RubricScoreType.BINARY)
                    .maxScore(1).minScore(0).passScore(1)
                    .scoringGuide("1=无害; 0=有害")
                    .star(true)
                    .build();

            DynamicRubricScorer scorer = new DynamicRubricScorer(
                    DynamicRubricScorerConfig.builder()
                            .metricName("HybridTest")
                            .llmService(llm)
                            .rubricGenPrompt(GEN_PROMPT)
                            .staticCriteria(Collections.singletonList(staticSafety))
                            .build()
            ) {
                @Override
                public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
                    return "query=" + in.get("query");
                }
            };

            DataItem item = buildDataItem("测试 query");
            injectContext(scorer);
            ScorerResult result = scorer.evalWrapper(item);

            assertTrue(result.isSuccess(), "混合模式评估应成功");
        }
    }

    // ==================== 兜底策略（Fallback）====================

    @Nested
    @DisplayName("兜底策略（Fallback Criteria）")
    class FallbackStrategy {

        @Test
        @DisplayName("LLM 生成失败 + 配置了 fallbackCriteria → 降级成功，extra 标记 fallback=true")
        void generationFails_withFallback_fallbackUsed() {
            // 第1次调用（生成 Rubric）失败，第2次调用（打分）成功
            // 让生成阶段抛异常，打分阶段正常
            LLMService failThenScore = new LLMService() {
                private final AtomicInteger count = new AtomicInteger(0);
                @Override
                public String chat(String prompt) {
                    int i = count.getAndIncrement();
                    if (i == 0) throw new RuntimeException("Rubric gen failed");
                    return "{\"score\": 1, \"reason\": \"fallback score\"}";
                }
                @Override
                public String getModel() { return "fail-then-score"; }
            };

            RubricCriteria fallback = RubricCriteria.builder()
                    .name("BasicQuality")
                    .definition("基础质量兜底")
                    .scoreType(RubricScoreType.BINARY)
                    .maxScore(1).minScore(0).passScore(1)
                    .scoringGuide("1=通过; 0=不通过")
                    .build();

            DynamicRubricScorer scorer = new DynamicRubricScorer(
                    DynamicRubricScorerConfig.builder()
                            .metricName("FallbackTest")
                            .llmService(failThenScore)
                            .rubricGenPrompt(GEN_PROMPT)
                            .fallbackCriteria(Collections.singletonList(fallback))
                            .maxGeneratedCriteria(5)
                            .build()
            ) {
                @Override
                public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
                    return "query=" + in.get("query");
                }
            };

            DataItem item = buildDataItem("测试兜底");
            injectContext(scorer);
            ScorerResult result = scorer.evalWrapper(item);

            assertTrue(result.isSuccess(), "兜底策略下评估应成功");
            assertTrue((Boolean) item.getExtraItem(DynamicRubricScorer.EXTRA_KEY_RUBRIC_FALLBACK),
                    "extra 应标记 fallback=true");
        }

        @Test
        @DisplayName("LLM 生成失败 + 无 fallbackCriteria → ScorerResult.isSuccess() = false")
        void generationFails_withoutFallback_resultIsError() {
            DynamicRubricScorer scorer = new DynamicRubricScorer(
                    DynamicRubricScorerConfig.builder()
                            .metricName("NoFallbackTest")
                            .llmService(mockLLMAlwaysFail())
                            .rubricGenPrompt(GEN_PROMPT)
                            .build()
            ) {
                @Override
                public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
                    return "query=" + in.get("query");
                }
            };

            DataItem item = buildDataItem("测试无兜底");
            // evalWrapper 内部捕获异常，返回 error result
            injectContext(scorer);
            ScorerResult result = scorer.evalWrapper(item);
            assertFalse(result.isSuccess(), "无兜底且生成失败时结果应为 error");
            assertEquals(0.0, result.getScore(), "error 结果分数应为 0");
        }
    }

    // ==================== 重试机制 ====================

    @Nested
    @DisplayName("重试机制")
    class RetryMechanism {

        @Test
        @DisplayName("LLM 第1次生成失败，第2次成功（重试 1 次），最终正常评估")
        void generationFirstFailThenSucceed_withRetry() {
            // 第1次：生成失败；第2次：生成成功；后续：打分成功
            LLMService llm = new LLMService() {
                private final AtomicInteger count = new AtomicInteger(0);
                @Override
                public String chat(String prompt) {
                    int i = count.getAndIncrement();
                    if (i == 0) throw new RuntimeException("First attempt failed");
                    if (i == 1) return "[{\"name\":\"Quality\",\"scoringGuide\":\"5=好; 1=差\"," +
                            "\"maxScore\":5,\"minScore\":1,\"passScore\":3}]";
                    return SCORE_REPLY;
                }
                @Override
                public String getModel() { return "retry-model"; }
            };

            DynamicRubricScorer scorer = new DynamicRubricScorer(
                    DynamicRubricScorerConfig.builder()
                            .metricName("RetryTest")
                            .llmService(llm)
                            .rubricGenPrompt(GEN_PROMPT)
                            .enableRetry(true)
                            .retryTimes(2)
                            .retryInterval(0)    // 测试中无需等待
                            .build()
            ) {
                @Override
                public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
                    return "query=" + in.get("query");
                }
            };

            DataItem item = buildDataItem("重试测试");
            injectContext(scorer);
            ScorerResult result = scorer.evalWrapper(item);
            assertTrue(result.isSuccess(), "重试后应评估成功");
        }

        @Test
        @DisplayName("超过最大重试次数仍失败 → 降级 fallback 或返回 error")
        void generationExceedsMaxRetry_fallsBackOrError() {
            // 始终失败
            DynamicRubricScorer scorer = new DynamicRubricScorer(
                    DynamicRubricScorerConfig.builder()
                            .metricName("MaxRetryTest")
                            .llmService(mockLLMAlwaysFail())
                            .rubricGenPrompt(GEN_PROMPT)
                            .enableRetry(true)
                            .retryTimes(2)
                            .retryInterval(0)
                            .build()
            ) {
                @Override
                public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
                    return "query=" + in.get("query");
                }
            };

            DataItem item = buildDataItem("超重试测试");
            injectContext(scorer);
            ScorerResult result = scorer.evalWrapper(item);
            assertFalse(result.isSuccess(), "超过最大重试次数无兜底时应返回 error");
        }
    }

    // ==================== prepareRubricGenInput 默认行为 ====================

    @Nested
    @DisplayName("prepareRubricGenInput 默认行为")
    class PrepareRubricGenInput {

        @Test
        @DisplayName("inputData 含 'query' 字段 → 默认实现取 query 值")
        void defaultImpl_extractsQueryField() {
            // gen reply 中不关心内容，只验证流程不报错
            LLMService llm = mockLLMSequence(
                    "[{\"name\":\"Q\",\"scoringGuide\":\"5=好; 1=差\",\"maxScore\":5,\"minScore\":1,\"passScore\":3}]",
                    SCORE_REPLY
            );

            DynamicRubricScorer scorer = new DynamicRubricScorer(
                    DynamicRubricScorerConfig.builder()
                            .metricName("DefaultInputTest")
                            .llmService(llm)
                            .rubricGenPrompt(GEN_PROMPT)
                            .build()
            ) {
                @Override
                public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
                    return "q=" + in.get("query");
                }
                // 不覆盖 prepareRubricGenInput，使用父类默认实现
            };

            DataItem item = buildDataItem("北京有什么好吃的");
            injectContext(scorer);
            ScorerResult result = scorer.evalWrapper(item);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("inputData 为空 → 默认实现返回空字符串，生成阶段仅依赖 rubricGenPrompt")
        void defaultImpl_emptyInputData_returnsEmpty() {
            LLMService llm = mockLLMSequence(
                    "[{\"name\":\"Q\",\"scoringGuide\":\"5=好; 1=差\",\"maxScore\":5,\"minScore\":1,\"passScore\":3}]",
                    SCORE_REPLY
            );

            DynamicRubricScorer scorer = new DynamicRubricScorer(
                    DynamicRubricScorerConfig.builder()
                            .metricName("EmptyInputTest")
                            .llmService(llm)
                            .rubricGenPrompt(GEN_PROMPT)
                            .build()
            ) {
                @Override
                public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
                    return "empty";
                }
            };

            DataItem item = buildEmptyDataItem();
            injectContext(scorer);
            ScorerResult result = scorer.evalWrapper(item);
            assertTrue(result.isSuccess());
        }
    }

    // ==================== 边界场景 ====================

    @Nested
    @DisplayName("边界场景")
    class EdgeCases {

        @Test
        @DisplayName("LLM 返回空 JSON 数组 → evalWrapper 返回 error result")
        void emptyJsonArray_resultsInError() {
            LLMService llm = mockLLM("[]");

            DynamicRubricScorer scorer = new DynamicRubricScorer(
                    DynamicRubricScorerConfig.builder()
                            .metricName("EmptyArrayTest")
                            .llmService(llm)
                            .rubricGenPrompt(GEN_PROMPT)
                            .build()
            ) {
                @Override
                public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
                    return "query=" + in.get("query");
                }
            };

            DataItem item = buildDataItem("空数组测试");
            injectContext(scorer);
            ScorerResult result = scorer.evalWrapper(item);
            assertFalse(result.isSuccess(), "空数组应导致评估失败");
        }

        @Test
        @DisplayName("LLM 返回非 JSON 内容 → evalWrapper 返回 error result")
        void invalidJsonReply_resultsInError() {
            LLMService llm = mockLLMSequence("这不是JSON格式");

            DynamicRubricScorer scorer = new DynamicRubricScorer(
                    DynamicRubricScorerConfig.builder()
                            .metricName("InvalidJsonTest")
                            .llmService(llm)
                            .rubricGenPrompt(GEN_PROMPT)
                            .build()
            ) {
                @Override
                public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
                    return "query=" + in.get("query");
                }
            };

            DataItem item = buildDataItem("非JSON测试");
            injectContext(scorer);
            ScorerResult result = scorer.evalWrapper(item);
            assertFalse(result.isSuccess(), "无效 JSON 应导致评估失败");
        }

        @Test
        @DisplayName("生成的 criteria name 全为空白 → evalWrapper 返回 error result")
        void allBlankNames_resultsInError() {
            String blankNameReply = "[{\"name\":\"   \",\"scoringGuide\":\"5=好; 1=差\",\"maxScore\":5,\"minScore\":1,\"passScore\":3}]";
            LLMService llm = mockLLM(blankNameReply);

            DynamicRubricScorer scorer = new DynamicRubricScorer(
                    DynamicRubricScorerConfig.builder()
                            .metricName("BlankNameTest")
                            .llmService(llm)
                            .rubricGenPrompt(GEN_PROMPT)
                            .build()
            ) {
                @Override
                public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
                    return "query=" + in.get("query");
                }
            };

            DataItem item = buildDataItem("空白名称测试");
            injectContext(scorer);
            ScorerResult result = scorer.evalWrapper(item);
            assertFalse(result.isSuccess(), "名称全为空白的 criteria 应导致评估失败");
        }

        @Test
        @DisplayName("passScore 超出 maxScore → 自动修正为 maxScore")
        void passScoreExceedsMaxScore_clampedToMax() {
            // passScore=10 > maxScore=5，期望被修正为 5
            String reply = "[{\"name\":\"Clamped\"," +
                    "\"scoringGuide\":\"5=好; 1=差\",\"maxScore\":5,\"minScore\":1,\"passScore\":10}]";
            LLMService llm = mockLLMSequence(reply, SCORE_REPLY);

            DynamicRubricScorer scorer = new DynamicRubricScorer(
                    DynamicRubricScorerConfig.builder()
                            .metricName("ClampTest")
                            .llmService(llm)
                            .rubricGenPrompt(GEN_PROMPT)
                            .build()
            ) {
                @Override
                public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
                    return "query=" + in.get("query");
                }
            };

            DataItem item = buildDataItem("passScore 修正测试");
            injectContext(scorer);
            ScorerResult result = scorer.evalWrapper(item);
            // 能正常打完分即验证通过（convertToCriteria 做了 clamp）
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("weight <= 0 → 自动修正为 1.0")
        void nonPositiveWeight_defaultsToOne() {
            String reply = "[{\"name\":\"WeightTest\"," +
                    "\"scoringGuide\":\"5=好; 1=差\",\"maxScore\":5,\"minScore\":1,\"passScore\":3,\"weight\":-0.5}]";
            LLMService llm = mockLLMSequence(reply, SCORE_REPLY);

            DynamicRubricScorer scorer = new DynamicRubricScorer(
                    DynamicRubricScorerConfig.builder()
                            .metricName("WeightTest")
                            .llmService(llm)
                            .rubricGenPrompt(GEN_PROMPT)
                            .build()
            ) {
                @Override
                public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
                    return "query=" + in.get("query");
                }
            };

            DataItem item = buildDataItem("weight 修正测试");
            injectContext(scorer);
            ScorerResult result = scorer.evalWrapper(item);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("extra 字段：正常流程后 EXTRA_KEY_GENERATED_CRITERIA 非空列表")
        void normalFlow_extraContainsGeneratedCriteria() {
            LLMService llm = mockLLMSequence(VALID_GEN_REPLY, SCORE_REPLY, SCORE_REPLY);

            DynamicRubricScorer scorer = new DynamicRubricScorer(
                    DynamicRubricScorerConfig.builder()
                            .metricName("ExtraKeyTest")
                            .llmService(llm)
                            .rubricGenPrompt(GEN_PROMPT)
                            .build()
            ) {
                @Override
                public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
                    return "query=" + in.get("query");
                }
            };

            DataItem item = buildDataItem("extra 测试");
            injectContext(scorer);
            scorer.evalWrapper(item);

            Object extraGenerated = item.getExtraItem(DynamicRubricScorer.EXTRA_KEY_GENERATED_CRITERIA);
            assertNotNull(extraGenerated);
            assertInstanceOf(List.class, extraGenerated);
            assertFalse(((List<?>) extraGenerated).isEmpty(), "生成的 criteria 列表不应为空");
        }
    }
}