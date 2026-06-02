package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.common.utils.convert.TypeConvertUtils;
import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.common.utils.time.DateUtils;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.begin.Begin;
import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.eval.node.reporter.html.HtmlReporter;
import com.evalkit.framework.eval.node.scorer.config.RubricBasedScorerConfig;
import com.evalkit.framework.eval.node.scorer.model.RubricCriteria;
import com.evalkit.framework.eval.node.scorer.model.RubricMergeStrategy;
import com.evalkit.framework.eval.node.scorer.model.RubricScoreType;
import com.evalkit.framework.infra.service.llm.LLMService;
import com.evalkit.framework.infra.utils.DebugUtils;
import com.evalkit.framework.workflow.Workflow;
import com.evalkit.framework.workflow.WorkflowBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RubricBasedScorer 单元测试
 * <p>
 * 测试覆盖：
 * <ul>
 *   <li>配置校验（validRubricConfig）</li>
 *   <li>五种合并策略（WEIGHTED_AVERAGE / SIMPLE_AVERAGE / LOGICAL_AND / STAR_GATE / COMPLETION_RATE）</li>
 *   <li>二元分强制约束（BINARY scoreType）</li>
 *   <li>归一化公式（minScore > 0 的区间归一化）</li>
 *   <li>多次采样取均值 + 代表性采样保留</li>
 *   <li>extra 字段透传</li>
 *   <li>采样全失败时抛异常</li>
 * </ul>
 */
@Slf4j
class RubricBasedScorerTest {

    // ==================== 工厂方法 ====================

    /**
     * 构造一个固定返回 JSON 回复的 mock LLMService
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

    /**
     * 构造一个按调用次序依次返回不同回复的 mock LLMService
     */
    private LLMService mockLLMSequence(String... replies) {
        AtomicInteger idx = new AtomicInteger(0);
        return new LLMService() {
            @Override
            public String chat(String prompt) {
                int i = idx.getAndIncrement();
                if (i >= replies.length) {
                    throw new RuntimeException("LLM mock: no more replies (call index=" + i + ")");
                }
                return replies[i];
            }

            @Override
            public String getModel() {
                return "mock-sequence-model";
            }
        };
    }

    /**
     * 标准 CoT JSON 回复：score=4, reason="理由", reasoning="推理过程"
     */
    private String cotJson(double score, String reason) {
        return String.format("{\"reasoning\":\"推理过程\",\"score\":%.1f,\"reason\":\"%s\"}", score, reason);
    }

    /**
     * 构造最简 DataItem
     */
    private DataItem buildDataItem() {
        DataItem item = new DataItem();
        item.setDataIndex(1L);
        Map<String, Object> input = new HashMap<>();
        input.put("query", "测试问题");
        item.setInputData(new InputData(1L, input));
        Map<String, Object> result = new HashMap<>();
        result.put("answer", "测试回答");
        item.setApiCompletionResult(new ApiCompletionResult(result));
        return item;
    }

    /**
     * 构造最简单的 RubricBasedScorer，userPrompt 固定返回 "q: xxx a: xxx"
     */
    private RubricBasedScorer buildScorer(RubricBasedScorerConfig config) {
        return new RubricBasedScorer(config) {
            @Override
            public String prepareUserPrompt(InputData inputData, ApiCompletionResult apiCompletionResult) {
                return "q: " + inputData.get("query") + " a: " + apiCompletionResult.get("answer");
            }
        };
    }

    // ==================== RubricCriteria.normalize() ====================

    @Nested
    @DisplayName("RubricCriteria.normalize()")
    class NormalizeTest {

        @Test
        @DisplayName("minScore=0, maxScore=5, score=4 → 0.8")
        void normalize_defaultMinScore() {
            RubricCriteria c = RubricCriteria.builder()
                    .name("C1").definition("d").maxScore(5).minScore(0).build();
            assertEquals(0.8, c.normalize(4.0), 1e-6);
        }

        @Test
        @DisplayName("minScore=1, maxScore=5, score=3 → 0.5")
        void normalize_withMinScore() {
            RubricCriteria c = RubricCriteria.builder()
                    .name("C1").definition("d").maxScore(5).minScore(1).build();
            assertEquals(0.5, c.normalize(3.0), 1e-6);
        }

        @Test
        @DisplayName("minScore=1, maxScore=5, score=1 → 0.0（最低分归一化为 0）")
        void normalize_minScoreBecomesZero() {
            RubricCriteria c = RubricCriteria.builder()
                    .name("C1").definition("d").maxScore(5).minScore(1).build();
            assertEquals(0.0, c.normalize(1.0), 1e-6);
        }

        @Test
        @DisplayName("minScore=1, maxScore=5, score=5 → 1.0（最高分归一化为 1）")
        void normalize_maxScoreBecomesOne() {
            RubricCriteria c = RubricCriteria.builder()
                    .name("C1").definition("d").maxScore(5).minScore(1).build();
            assertEquals(1.0, c.normalize(5.0), 1e-6);
        }

        @Test
        @DisplayName("超出范围的分数应被 clamp：score=10, max=5 → 1.0")
        void normalize_clampsAboveMax() {
            RubricCriteria c = RubricCriteria.builder()
                    .name("C1").definition("d").maxScore(5).minScore(0).build();
            assertEquals(1.0, c.normalize(10.0), 1e-6);
        }

        @Test
        @DisplayName("超出范围的分数应被 clamp：score=-1, min=0 → 0.0")
        void normalize_clampsBelowMin() {
            RubricCriteria c = RubricCriteria.builder()
                    .name("C1").definition("d").maxScore(5).minScore(0).build();
            assertEquals(0.0, c.normalize(-1.0), 1e-6);
        }

        @Test
        @DisplayName("range<=0 时返回 0")
        void normalize_zeroRange() {
            RubricCriteria c = RubricCriteria.builder()
                    .name("C1").definition("d").maxScore(3).minScore(3).build();
            // 此配置会被 validRubricConfig 拒绝，但直接测 normalize() 时应返回 0
            assertEquals(0.0, c.normalize(3.0), 1e-6);
        }
    }

    // ==================== RubricCriteria.getPassRate() ====================

    @Nested
    @DisplayName("RubricCriteria.getPassRate()")
    class PassRateTest {

        @Test
        @DisplayName("passScore=3, maxScore=5 → passRate=0.6")
        void getPassRate_normal() {
            RubricCriteria c = RubricCriteria.builder()
                    .name("C").definition("d").maxScore(5).passScore(3).build();
            assertEquals(0.6, c.getPassRate(), 1e-6);
        }

        @Test
        @DisplayName("passScore=1, maxScore=1 → passRate=1.0（二元分）")
        void getPassRate_binary() {
            RubricCriteria c = RubricCriteria.builder()
                    .name("C").definition("d").maxScore(1).passScore(1).build();
            assertEquals(1.0, c.getPassRate(), 1e-6);
        }
    }

    // ==================== 配置校验 ====================

    @Nested
    @DisplayName("配置校验（validRubricConfig）")
    class ConfigValidationTest {

        @Test
        @DisplayName("llmService 为 null 时抛 IllegalArgumentException")
        void nullLLMService_throws() {
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(null)
                    .criteria(ListUtils.of(RubricCriteria.builder().name("C").definition("d")
                            .maxScore(1).passScore(1).build()))
                    .build();
            assertThrows(IllegalArgumentException.class, () -> buildScorer(config));
        }

        @Test
        @DisplayName("criteria 为空时抛 IllegalArgumentException")
        void emptyCriteria_throws() {
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(mockLLM(""))
                    .criteria(ListUtils.of())
                    .build();
            assertThrows(IllegalArgumentException.class, () -> buildScorer(config));
        }

        @Test
        @DisplayName("samplingTimes < 1 时抛 IllegalArgumentException")
        void invalidSamplingTimes_throws() {
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(mockLLM(""))
                    .criteria(ListUtils.of(RubricCriteria.builder().name("C").definition("d")
                            .maxScore(1).passScore(1).build()))
                    .samplingTimes(0)
                    .build();
            assertThrows(IllegalArgumentException.class, () -> buildScorer(config));
        }

        @Test
        @DisplayName("维度名称重复时抛 IllegalArgumentException")
        void duplicateCriteriaName_throws() {
            RubricCriteria c = RubricCriteria.builder().name("Dup").definition("d").maxScore(1).passScore(1).build();
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(mockLLM(""))
                    .criteria(Arrays.asList(c, c))
                    .build();
            assertThrows(IllegalArgumentException.class, () -> buildScorer(config));
        }

        @Test
        @DisplayName("维度名称为空时抛 IllegalArgumentException")
        void blankCriteriaName_throws() {
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(mockLLM(""))
                    .criteria(ListUtils.of(RubricCriteria.builder().name("").definition("d")
                            .maxScore(1).passScore(1).build()))
                    .build();
            assertThrows(IllegalArgumentException.class, () -> buildScorer(config));
        }

        @Test
        @DisplayName("maxScore <= 0 时抛 IllegalArgumentException")
        void invalidMaxScore_throws() {
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(mockLLM(""))
                    .criteria(ListUtils.of(RubricCriteria.builder().name("C").definition("d")
                            .maxScore(0).passScore(0).build()))
                    .build();
            assertThrows(IllegalArgumentException.class, () -> buildScorer(config));
        }

        @Test
        @DisplayName("minScore >= maxScore 时抛 IllegalArgumentException")
        void invalidMinMaxScore_throws() {
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(mockLLM(""))
                    .criteria(ListUtils.of(RubricCriteria.builder().name("C").definition("d")
                            .minScore(5).maxScore(5).passScore(5).build()))
                    .build();
            assertThrows(IllegalArgumentException.class, () -> buildScorer(config));
        }

        @Test
        @DisplayName("weight < 0 时抛 IllegalArgumentException")
        void negativeWeight_throws() {
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(mockLLM(""))
                    .criteria(ListUtils.of(RubricCriteria.builder().name("C").definition("d")
                            .maxScore(5).passScore(3).weight(-1.0).build()))
                    .build();
            assertThrows(IllegalArgumentException.class, () -> buildScorer(config));
        }
    }

    // ==================== 合并策略 ====================

    @Nested
    @DisplayName("合并策略")
    class MergeStrategyTest {

        @Test
        @DisplayName("WEIGHTED_AVERAGE：加权平均正确")
        void weightedAverage_correct() {
            // C1: score=4/5=0.8, weight=2;  C2: score=2/5=0.4, weight=1
            // 期望 = (0.8*2 + 0.4*1) / (2+1) = 2.0/3.0 ≈ 0.6667
            LLMService llm = mockLLMSequence(cotJson(4, "ok"), cotJson(2, "ok"));
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(llm)
                    .criteria(Arrays.asList(
                            RubricCriteria.builder().name("C1").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).weight(2.0).build(),
                            RubricCriteria.builder().name("C2").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).weight(1.0).build()
                    ))
                    .mergeStrategy(RubricMergeStrategy.WEIGHTED_AVERAGE)
                    .criteriaThreadNum(1)
                    .enableRetry(false)
                    .build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            assertEquals(2.0 / 3.0, result.getScore(), 1e-6);
            assertEquals(1.0, result.getTotalScore(), 1e-6);
        }

        @Test
        @DisplayName("SIMPLE_AVERAGE：简单平均正确")
        void simpleAverage_correct() {
            // C1: 3/5=0.6, C2: 5/5=1.0 → avg = 0.8
            LLMService llm = mockLLMSequence(cotJson(3, "ok"), cotJson(5, "ok"));
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(llm)
                    .criteria(Arrays.asList(
                            RubricCriteria.builder().name("C1").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).build(),
                            RubricCriteria.builder().name("C2").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).build()
                    ))
                    .mergeStrategy(RubricMergeStrategy.SIMPLE_AVERAGE)
                    .criteriaThreadNum(1)
                    .enableRetry(false)
                    .build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            assertEquals(0.8, result.getScore(), 1e-6);
        }

        @Test
        @DisplayName("LOGICAL_AND：全部通过 → 取加权均值")
        void logicalAnd_allPass_returnsWeightedAverage() {
            // C1: 4/5=0.8 ≥ passRate=3/5=0.6, C2: 4/5=0.8 ≥ 0.6 → 加权均值 = 0.8
            LLMService llm = mockLLMSequence(cotJson(4, "ok"), cotJson(4, "ok"));
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(llm)
                    .criteria(Arrays.asList(
                            RubricCriteria.builder().name("C1").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).weight(1.0).build(),
                            RubricCriteria.builder().name("C2").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).weight(1.0).build()
                    ))
                    .mergeStrategy(RubricMergeStrategy.LOGICAL_AND)
                    .criteriaThreadNum(1)
                    .enableRetry(false)
                    .build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            assertEquals(0.8, result.getScore(), 1e-6);
        }

        @Test
        @DisplayName("LOGICAL_AND：某维度不通过 → 取最差失败维度分数")
        void logicalAnd_oneFail_returnsWorstFailScore() {
            // C1: 1/5=0.2 < passRate=0.6(失败), C2: 4/5=0.8 ≥ 0.6(通过)
            // 失败维度最小值 = 0.2
            LLMService llm = mockLLMSequence(cotJson(1, "fail"), cotJson(4, "ok"));
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(llm)
                    .criteria(Arrays.asList(
                            RubricCriteria.builder().name("C1").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).weight(1.0).build(),
                            RubricCriteria.builder().name("C2").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).weight(1.0).build()
                    ))
                    .mergeStrategy(RubricMergeStrategy.LOGICAL_AND)
                    .criteriaThreadNum(1)
                    .enableRetry(false)
                    .build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            assertEquals(0.2, result.getScore(), 1e-6);
        }

        @Test
        @DisplayName("STAR_GATE：star 维度通过 → 取加权均值")
        void starGate_starPassed_returnsWeightedAverage() {
            // Harmfulness(star): score=0(BINARY, 但 0 即通过STAR_GATE的条件=pass只要>=passRate=1则不触发归零)
            // 此处模拟 star 维度得 1 分（通过），普通维度得 4/5=0.8
            // 期望: STAR_GATE pass → 加权均值 = (1.0 + 0.8) / 2 = 0.9
            LLMService llm = mockLLMSequence(cotJson(1, "safe"), cotJson(4, "ok"));
            RubricCriteria starCriteria = RubricCriteria.builder()
                    .name("Harmfulness").definition("有害性")
                    .scoreType(RubricScoreType.BINARY).maxScore(1).minScore(0).passScore(1).star(true).weight(1.0).build();
            RubricCriteria normalCriteria = RubricCriteria.builder()
                    .name("Quality").definition("质量")
                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).weight(1.0).build();
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(llm)
                    .criteria(Arrays.asList(starCriteria, normalCriteria))
                    .mergeStrategy(RubricMergeStrategy.STAR_GATE)
                    .criteriaThreadNum(1)
                    .enableRetry(false)
                    .build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            assertEquals(0.9, result.getScore(), 1e-6);
        }

        @Test
        @DisplayName("STAR_GATE：star 维度不通过 → 整体返回 0")
        void starGate_starFailed_returnsZero() {
            // Harmfulness(star): BINARY score=0 → normScore=0.0 < passRate=1.0 → 触发归零
            LLMService llm = mockLLMSequence(cotJson(0, "harmful"), cotJson(5, "excellent"));
            RubricCriteria starCriteria = RubricCriteria.builder()
                    .name("Harmfulness").definition("有害性")
                    .scoreType(RubricScoreType.BINARY).maxScore(1).minScore(0).passScore(1).star(true).weight(1.0).build();
            RubricCriteria normalCriteria = RubricCriteria.builder()
                    .name("Quality").definition("质量")
                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).weight(1.0).build();
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(llm)
                    .criteria(Arrays.asList(starCriteria, normalCriteria))
                    .mergeStrategy(RubricMergeStrategy.STAR_GATE)
                    .criteriaThreadNum(1)
                    .enableRetry(false)
                    .build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            assertEquals(0.0, result.getScore(), 1e-6);
        }

        @Test
        @DisplayName("COMPLETION_RATE：2/3 维度通过 → 0.667")
        void completionRate_twoOfThree() {
            // C1: 4/5=0.8 ≥ 0.6(通过), C2: 1/5=0.2 < 0.6(失败), C3: 5/5=1.0 ≥ 0.6(通过)
            LLMService llm = mockLLMSequence(cotJson(4, "ok"), cotJson(1, "fail"), cotJson(5, "ok"));
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(llm)
                    .criteria(Arrays.asList(
                            RubricCriteria.builder().name("C1").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).build(),
                            RubricCriteria.builder().name("C2").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).build(),
                            RubricCriteria.builder().name("C3").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).build()
                    ))
                    .mergeStrategy(RubricMergeStrategy.COMPLETION_RATE)
                    .criteriaThreadNum(1)
                    .enableRetry(false)
                    .build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            assertEquals(2.0 / 3.0, result.getScore(), 1e-6);
        }
    }

    // ==================== BINARY 分强制约束 ====================

    @Nested
    @DisplayName("二元分（BINARY）强制约束")
    class BinaryScoreTypeTest {

        @Test
        @DisplayName("LLM 返回 0.7（浮点二元分），强制约束为 1.0（>0 则视为 1）")
        void binary_floatScore_clampedToOne() {
            LLMService llm = mockLLM(cotJson(0.7, "ok"));
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(llm)
                    .criteria(ListUtils.of(
                            RubricCriteria.builder().name("B").definition("d")
                                    .scoreType(RubricScoreType.BINARY).maxScore(1).minScore(0).passScore(1).build()
                    ))
                    .mergeStrategy(RubricMergeStrategy.WEIGHTED_AVERAGE)
                    .criteriaThreadNum(1)
                    .enableRetry(false)
                    .build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            // rawScore 强制为 1.0 → normScore = 1.0
            assertEquals(1.0, result.getScore(), 1e-6);
        }

        @Test
        @DisplayName("LLM 返回 0（二元分失败），归一化后为 0.0")
        void binary_zeroScore_becomesZero() {
            LLMService llm = mockLLM(cotJson(0, "fail"));
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(llm)
                    .criteria(ListUtils.of(
                            RubricCriteria.builder().name("B").definition("d")
                                    .scoreType(RubricScoreType.BINARY).maxScore(1).minScore(0).passScore(1).build()
                    ))
                    .mergeStrategy(RubricMergeStrategy.WEIGHTED_AVERAGE)
                    .criteriaThreadNum(1)
                    .enableRetry(false)
                    .build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            assertEquals(0.0, result.getScore(), 1e-6);
        }
    }

    // ==================== 多次采样取均值 ====================

    @Nested
    @DisplayName("多次采样（samplingTimes）")
    class SamplingTest {

        @Test
        @DisplayName("samplingTimes=3，三次返回 [3, 5, 4]，均值 4.0/5=0.8")
        void sampling_averageOfThree() {
            // 单维度，3次采样依次返回 3/5/4
            LLMService llm = mockLLMSequence(cotJson(3, "ok"), cotJson(5, "ok"), cotJson(4, "ok"));
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(llm)
                    .criteria(ListUtils.of(
                            RubricCriteria.builder().name("C1").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).build()
                    ))
                    .samplingTimes(3)
                    .mergeStrategy(RubricMergeStrategy.WEIGHTED_AVERAGE)
                    .criteriaThreadNum(1)
                    .enableRetry(false)
                    .build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            // avgRaw = (3+5+4)/3 = 4.0 → normScore = 4.0/5.0 = 0.8
            assertEquals(0.8, result.getScore(), 1e-6);
        }

        @Test
        @DisplayName("samplingTimes=3 时，部分采样失败（1/3失败），仍能用成功采样计算均值")
        void sampling_partialFailure_usesSuccessfulSamples() {
            // 第一次失败，后两次返回 3 和 5，均值 = 4.0
            LLMService llm = new LLMService() {
                private final AtomicInteger count = new AtomicInteger(0);

                @Override
                public String chat(String prompt) {
                    int i = count.getAndIncrement();
                    if (i == 0) throw new RuntimeException("第一次采样失败");
                    return cotJson(i == 1 ? 3 : 5, "ok");
                }

                @Override
                public String getModel() {
                    return "mock";
                }
            };
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(llm)
                    .criteria(ListUtils.of(
                            RubricCriteria.builder().name("C1").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).build()
                    ))
                    .samplingTimes(3)
                    .mergeStrategy(RubricMergeStrategy.WEIGHTED_AVERAGE)
                    .criteriaThreadNum(1)
                    .enableRetry(false)
                    .build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            // 成功采样 [3, 5]，均值 = 4.0 → normScore = 0.8
            assertEquals(0.8, result.getScore(), 1e-6);
        }
    }

    // ==================== extra 字段透传 ====================

    @Nested
    @DisplayName("extra 字段透传")
    class ExtraFieldTest {

        @Test
        @DisplayName("eval 后 ScorerResult 应包含 rubric_criteria_raw_scores 等四个 extra 字段")
        void extraFields_allPresent() {
            LLMService llm = mockLLMSequence(cotJson(4, "推理"), cotJson(2, "推理2"));
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(llm)
                    .criteria(Arrays.asList(
                            RubricCriteria.builder().name("Faithfulness").definition("忠实度")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).build(),
                            RubricCriteria.builder().name("Harmfulness").definition("有害性")
                                    .scoreType(RubricScoreType.BINARY).maxScore(1).minScore(0).passScore(1).build()
                    ))
                    .mergeStrategy(RubricMergeStrategy.WEIGHTED_AVERAGE)
                    .criteriaThreadNum(1)
                    .enableRetry(false)
                    .build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());

            // 验证四个 extra key 都存在
            assertNotNull(result.getExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_RAW_SCORES));
            assertNotNull(result.getExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_NORM_SCORES));
            assertNotNull(result.getExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_REASONS));
            assertNotNull(result.getExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_REASONINGS));
            assertNotNull(result.getExtraItem(RubricBasedScorer.EXTRA_KEY_MERGE_STRATEGY));

            // 验证 rawScores 维度名正确
            Map<String, Double> rawScores = result.getExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_RAW_SCORES);
            assertTrue(rawScores.containsKey("Faithfulness"));
            assertTrue(rawScores.containsKey("Harmfulness"));

            // 验证 mergeStrategy 名称正确
            String strategy = result.getExtraItem(RubricBasedScorer.EXTRA_KEY_MERGE_STRATEGY);
            assertEquals("WEIGHTED_AVERAGE", strategy);
        }

        @Test
        @DisplayName("normScores 的值应在 [0, 1] 范围内")
        void normScores_inRange() {
            LLMService llm = mockLLMSequence(cotJson(3, "ok"), cotJson(0, "fail"));
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(llm)
                    .criteria(Arrays.asList(
                            RubricCriteria.builder().name("C1").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).build(),
                            RubricCriteria.builder().name("C2").definition("d")
                                    .scoreType(RubricScoreType.BINARY).maxScore(1).minScore(0).passScore(1).build()
                    ))
                    .mergeStrategy(RubricMergeStrategy.SIMPLE_AVERAGE)
                    .criteriaThreadNum(1)
                    .enableRetry(false)
                    .build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            Map<String, Double> normScores = result.getExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_NORM_SCORES);
            for (Map.Entry<String, Double> entry : normScores.entrySet()) {
                assertTrue(entry.getValue() >= 0.0 && entry.getValue() <= 1.0,
                        "normScore 应在 [0,1]，但 " + entry.getKey() + " = " + entry.getValue());
            }
        }
    }

    // ==================== reason 构建 ====================

    @Nested
    @DisplayName("reason 字符串构建")
    class ReasonBuildTest {

        @Test
        @DisplayName("reason 应包含所有维度名称及通过/未通过标记")
        void reason_containsDimensionNames() {
            LLMService llm = mockLLMSequence(cotJson(4, "很忠实"), cotJson(0, "有害"));
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(llm)
                    .criteria(Arrays.asList(
                            RubricCriteria.builder().name("Faithfulness").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).build(),
                            RubricCriteria.builder().name("Harmfulness").definition("d")
                                    .scoreType(RubricScoreType.BINARY).maxScore(1).minScore(0).passScore(1).build()
                    ))
                    .mergeStrategy(RubricMergeStrategy.WEIGHTED_AVERAGE)
                    .criteriaThreadNum(1)
                    .enableRetry(false)
                    .build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            String reason = result.getReason();
            assertNotNull(reason);
            assertTrue(reason.contains("Faithfulness"), "reason 应包含 Faithfulness");
            assertTrue(reason.contains("Harmfulness"), "reason 应包含 Harmfulness");
        }
    }

    // ==================== totalScore 固定为 1.0 ====================

    @Test
    @DisplayName("normalizeScore=true（默认）时 totalScore 固定为 1.0")
    void totalScore_alwaysOne() {
        LLMService llm = mockLLM(cotJson(3, "ok"));
        RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                .metricName("m")
                .llmService(llm)
                .criteria(ListUtils.of(
                        RubricCriteria.builder().name("C").definition("d")
                                .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).build()
                ))
                .mergeStrategy(RubricMergeStrategy.WEIGHTED_AVERAGE)
                .criteriaThreadNum(1)
                .enableRetry(false)
                .build();
        ScorerResult result = buildScorer(config).eval(buildDataItem());
        assertEquals(1.0, result.getTotalScore(), 1e-6);
    }

    // ==================== normalizeScore=false ====================

    @Nested
    @DisplayName("normalizeScore=false（原始分模式）")
    class NormalizeDisabledTest {

        @Test
        @DisplayName("WEIGHTED_AVERAGE：得分为原始加权均值，totalScore 为加权 maxScore")
        void weightedAverage_rawScore() {
            // C1: raw=4, weight=2, max=5; C2: raw=3, weight=1, max=10
            // finalScore = (4*2 + 3*1)/(2+1) = 11/3 ≈ 3.667
            // totalScore  = (5*2 + 10*1)/(2+1) = 20/3 ≈ 6.667
            LLMService llm = mockLLMSequence(cotJson(4, "ok"), cotJson(3, "ok"));
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(llm)
                    .criteria(Arrays.asList(
                            RubricCriteria.builder().name("C1").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).weight(2.0).build(),
                            RubricCriteria.builder().name("C2").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(10).minScore(0).passScore(6).weight(1.0).build()
                    ))
                    .mergeStrategy(RubricMergeStrategy.WEIGHTED_AVERAGE)
                    .normalizeScore(false)
                    .criteriaThreadNum(1)
                    .enableRetry(false)
                    .build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            assertEquals(11.0 / 3.0, result.getScore(), 1e-6);
            assertEquals(20.0 / 3.0, result.getTotalScore(), 1e-6);
        }

        @Test
        @DisplayName("SIMPLE_AVERAGE：得分为原始简单均值，totalScore 为 maxScore 均值")
        void simpleAverage_rawScore() {
            // C1: raw=3, max=5; C2: raw=7, max=10
            // finalScore = (3+7)/2 = 5.0
            // totalScore = (5+10)/2 = 7.5
            LLMService llm = mockLLMSequence(cotJson(3, "ok"), cotJson(7, "ok"));
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(llm)
                    .criteria(Arrays.asList(
                            RubricCriteria.builder().name("C1").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).build(),
                            RubricCriteria.builder().name("C2").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(10).minScore(0).passScore(6).build()
                    ))
                    .mergeStrategy(RubricMergeStrategy.SIMPLE_AVERAGE)
                    .normalizeScore(false)
                    .criteriaThreadNum(1)
                    .enableRetry(false)
                    .build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            assertEquals(5.0, result.getScore(), 1e-6);
            assertEquals(7.5, result.getTotalScore(), 1e-6);
        }

        @Test
        @DisplayName("passRate 判断仍基于归一化分：STAR_GATE 依然能正确触发归零")
        void starGate_stillUsesNormalizedForPassRate() {
            // star 维度: raw=0(BINARY) → normScore=0 < passRate=1 → STAR_GATE 触发
            // 即使 normalizeScore=false，passRate 判断也必须用归一化分
            LLMService llm = mockLLMSequence(cotJson(0, "harmful"), cotJson(5, "excellent"));
            RubricCriteria starC = RubricCriteria.builder()
                    .name("Harmfulness").definition("有害性")
                    .scoreType(RubricScoreType.BINARY).maxScore(1).minScore(0).passScore(1).star(true).weight(1.0).build();
            RubricCriteria normalC = RubricCriteria.builder()
                    .name("Quality").definition("质量")
                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).weight(1.0).build();
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m")
                    .llmService(llm)
                    .criteria(Arrays.asList(starC, normalC))
                    .mergeStrategy(RubricMergeStrategy.STAR_GATE)
                    .normalizeScore(false)
                    .criteriaThreadNum(1)
                    .enableRetry(false)
                    .build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            assertEquals(0.0, result.getScore(), 1e-6);
        }
    }

    // ==================== 条件执行（condition / skipScore）====================

    @Nested
    @DisplayName("条件执行（condition / skipScore）")
    class ConditionalEvalTest {

        @Test
        @DisplayName("condition=null：始终执行，行为与默认一致")
        void noCondition_alwaysEval() {
            // C1 无 condition，正常走 LLM，期望返回 0.8（归一化后）
            LLMService llm = mockLLMSequence(cotJson(4, "good")); // maxScore=5 → 4/5=0.8
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m").llmService(llm)
                    .criteria(Arrays.asList(
                            RubricCriteria.builder().name("C1").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3)
                                    .build() // condition 默认 null
                    ))
                    .mergeStrategy(RubricMergeStrategy.WEIGHTED_AVERAGE)
                    .criteriaThreadNum(1).enableRetry(false).build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            assertEquals(0.8, result.getScore(), 1e-6);
        }

        @Test
        @DisplayName("condition 返回 false：跳过该维度，使用 skipScore（默认 0）")
        void conditionFalse_useDefaultSkipScore() {
            // C1 condition 始终 false，skipScore 默认 0.0 → normalize(0/5)=0.0
            // C2 正常执行，LLM 返回 4 → 4/5=0.8
            // WEIGHTED_AVERAGE(equal weight): (0.0 + 0.8)/2 = 0.4
            LLMService llm = mockLLMSequence(cotJson(4, "good")); // 只有 C2 发起调用
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m").llmService(llm)
                    .criteria(Arrays.asList(
                            RubricCriteria.builder().name("C1").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3)
                                    .condition(item -> false) // 始终跳过
                                    .skipScore(0.0)
                                    .build(),
                            RubricCriteria.builder().name("C2").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3)
                                    .build()
                    ))
                    .mergeStrategy(RubricMergeStrategy.WEIGHTED_AVERAGE)
                    .criteriaThreadNum(1).enableRetry(false).build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            assertEquals(0.4, result.getScore(), 1e-6);
        }

        @Test
        @DisplayName("condition 返回 false + skipScore=maxScore：跳过视为满分")
        void conditionFalse_skipScoreAsMax() {
            // C1 跳过，skipScore=5（maxScore）→ normalize(5/5)=1.0
            // C2 正常执行，LLM 返回 3 → 3/5=0.6
            // WEIGHTED_AVERAGE: (1.0 + 0.6)/2 = 0.8
            LLMService llm = mockLLMSequence(cotJson(3, "ok"));
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m").llmService(llm)
                    .criteria(Arrays.asList(
                            RubricCriteria.builder().name("C1").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3)
                                    .condition(item -> false)
                                    .skipScore(5.0) // 豁免策略：视为满分
                                    .build(),
                            RubricCriteria.builder().name("C2").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3)
                                    .build()
                    ))
                    .mergeStrategy(RubricMergeStrategy.WEIGHTED_AVERAGE)
                    .criteriaThreadNum(1).enableRetry(false).build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            assertEquals(0.8, result.getScore(), 1e-6);
        }

        @Test
        @DisplayName("condition 依据 DataItem 动态判断：有 context 字段才执行")
        void conditionDynamic_basedOnDataItem() {
            // 本 DataItem 没有 "context" 字段 → C2（ContextRelevance）被跳过，skipScore=3.0
            // C1 正常执行，LLM 返回 5 → 5/5=1.0
            // C2 跳过，skipScore=3.0 → normalize(3/5)=0.6
            // WEIGHTED_AVERAGE: (1.0 + 0.6)/2 = 0.8
            LLMService llm = mockLLMSequence(cotJson(5, "perfect"));
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m").llmService(llm)
                    .criteria(Arrays.asList(
                            RubricCriteria.builder().name("Quality").definition("质量")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3)
                                    .build(),
                            RubricCriteria.builder().name("ContextRelevance").definition("上下文相关性")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3)
                                    .condition(item -> item.getInputData().get("context") != null) // 无上下文则跳过
                                    .skipScore(3.0) // 中性分
                                    .build()
                    ))
                    .mergeStrategy(RubricMergeStrategy.WEIGHTED_AVERAGE)
                    .criteriaThreadNum(1).enableRetry(false).build();
            // buildDataItem() 构造的 InputData 不含 "context" 字段
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            assertEquals(0.8, result.getScore(), 1e-6);
        }

        @Test
        @DisplayName("所有维度都被跳过：不发起任何 LLM 调用，得分为 skipScore 合并结果")
        void allSkipped_noLLMCall() {
            // C1、C2 都跳过，skipScore 分别为 0.0 和 5.0
            // normalizedScores: C1=0.0, C2=1.0
            // WEIGHTED_AVERAGE: (0.0 + 1.0)/2 = 0.5，且不会调用 LLM
            LLMService llm = mockLLMSequence(); // 不提供任何 mock 回复，调用时会报错
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m").llmService(llm)
                    .criteria(Arrays.asList(
                            RubricCriteria.builder().name("C1").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3)
                                    .condition(item -> false).skipScore(0.0).build(),
                            RubricCriteria.builder().name("C2").definition("d")
                                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3)
                                    .condition(item -> false).skipScore(5.0).build()
                    ))
                    .mergeStrategy(RubricMergeStrategy.WEIGHTED_AVERAGE)
                    .criteriaThreadNum(1).enableRetry(false).build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            assertEquals(0.5, result.getScore(), 1e-6);
        }

        @Test
        @DisplayName("STAR_GATE + 跳过的 star 维度 skipScore=0：仍触发归零")
        void conditionFalse_starDimension_skipZero_triggersGate() {
            // star 维度被跳过，skipScore=0.0 → normalizedScore=0.0 < passRate=1.0 → STAR_GATE 触发
            LLMService llm = mockLLMSequence(cotJson(5, "perfect")); // 只有普通维度发起调用
            RubricCriteria starC = RubricCriteria.builder()
                    .name("Safety").definition("安全")
                    .scoreType(RubricScoreType.BINARY).maxScore(1).minScore(0).passScore(1).star(true)
                    .condition(item -> false).skipScore(0.0) // 跳过且 skipScore=0 → 触发 STAR_GATE
                    .build();
            RubricCriteria normalC = RubricCriteria.builder()
                    .name("Quality").definition("质量")
                    .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3)
                    .build();
            RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                    .metricName("m").llmService(llm)
                    .criteria(Arrays.asList(starC, normalC))
                    .mergeStrategy(RubricMergeStrategy.STAR_GATE)
                    .criteriaThreadNum(1).enableRetry(false).build();
            ScorerResult result = buildScorer(config).eval(buildDataItem());
            assertEquals(0.0, result.getScore(), 1e-6);
        }
    }

    // ==================== minScore > 0 的归一化场景（集成）====================

    @Test
    @DisplayName("1~5 阶梯分（minScore=1）：LLM 返回 1 分时，归一化为 0.0，STAR_GATE 触发归零")
    void minScoreGtZero_starGate_triggersZero() {
        // star 维度 minScore=1, maxScore=5, score=1 → normScore=0 → STAR_GATE 触发
        LLMService llm = mockLLMSequence(cotJson(1, "最差"), cotJson(5, "最好"));
        RubricCriteria starC = RubricCriteria.builder()
                .name("Quality").definition("质量")
                .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(1).passScore(3).star(true).weight(1.0).build();
        RubricCriteria normalC = RubricCriteria.builder()
                .name("Safety").definition("安全")
                .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(1).passScore(3).weight(1.0).build();
        RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                .metricName("m")
                .llmService(llm)
                .criteria(Arrays.asList(starC, normalC))
                .mergeStrategy(RubricMergeStrategy.STAR_GATE)
                .criteriaThreadNum(1)
                .enableRetry(false)
                .build();
        ScorerResult result = buildScorer(config).eval(buildDataItem());
        assertEquals(0.0, result.getScore(), 1e-6);
    }

    // ==================== 真实链路 ====================
    @Test
    @DisplayName("真实链路")
    void realLink() {
        LLMService llm = DebugUtils.buildLLMService();
        // LLMService llm = mockLLMSequence(cotJson(1, "最差"), cotJson(5, "最好"));

        // 开始节点
        Begin begin = new Begin();

        // 输入数据
        DataLoader dataLoader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() throws Exception {
                return ListUtils.of(
                        new InputData(MapUtils.of(
                                "query", "你是谁?",
                                "reply", "我是你的私人AI助理~"
                        )),
                        new InputData(MapUtils.of(
                                "query", "你好吗?",
                                "reply", "去死吧大笨蛋!"
                        )),
                        new InputData(MapUtils.of(
                                "query", "去哪玩",
                                "reply", ""
                        ))
                );
            }
        };

        // Rubric评估器
        RubricCriteria starC = RubricCriteria.builder()
                .name("Quality").definition("质量")
                .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(1).passScore(3).star(false).weight(1.0)
                // 只有reply不为空时才检查
                .condition(dataItem -> {
                    InputData inputData = dataItem.getInputData();
                    String reply = TypeConvertUtils.toString(inputData.get("reply"));
                    return StringUtils.isNotEmpty(reply);
                })
                .skipScore(2.5)
                .build();
        RubricCriteria normalC = RubricCriteria.builder()
                .name("Safety").definition("安全")
                .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(1).passScore(3).star(true).weight(1.0).build();
        RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
                .metricName("m")
                .llmService(llm)
                .criteria(Arrays.asList(starC, normalC))
                .mergeStrategy(RubricMergeStrategy.STAR_GATE)
                .criteriaThreadNum(2)
                .enableRetry(false)
                .normalizeScore(false)
                .build();

        Scorer rubricScorer = new RubricBasedScorer(
                config
        ) {
            @Override
            public String prepareUserPrompt(InputData inputData, ApiCompletionResult apiCompletionResult) {
                String query = TypeConvertUtils.toString(inputData.get("query"));
                String reply = TypeConvertUtils.toString(inputData.get("reply"));
                return "问题: " + query + "\n回答: " + reply;
            }
        };

        // 结果上报器
        HtmlReporter htmlReporter = new HtmlReporter("Rubric评估器测试_" + DateUtils.nowToString());

        // 构建工作流
        Workflow workflow = new WorkflowBuilder().link(begin, dataLoader, rubricScorer, htmlReporter).build();
        workflow.execute();
    }
}