package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.scorer.config.ScorerConfig;
import com.evalkit.framework.eval.node.scorer.strategy.AvgScoreRateStrategy;
import com.evalkit.framework.eval.node.scorer.strategy.SumScoreStrategy;
import com.evalkit.framework.workflow.model.WorkflowContext;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class ScorerTest {

    /**
     * 构造一个最简单的具体 Scorer，始终返回指定分数
     */
    private Scorer buildScorer(String metric, double totalScore, double threshold, boolean star, double returnScore) {
        ScorerConfig cfg = ScorerConfig.builder()
                .metricName(metric)
                .totalScore(totalScore)
                .threshold(threshold)
                .star(star)
                .build();
        return new Scorer(cfg) {
            @Override
            public ScorerResult eval(DataItem dataItem) {
                return new ScorerResult(metric, returnScore, totalScore, "理由");
            }
        };
    }

    /**
     * 构造一个始终抛异常的 Scorer
     */
    private Scorer buildThrowingScorer(String metric) {
        ScorerConfig cfg = ScorerConfig.builder().metricName(metric).build();
        return new Scorer(cfg) {
            @Override
            public ScorerResult eval(DataItem dataItem) throws Exception {
                throw new RuntimeException("故意抛出的异常");
            }
        };
    }

    /**
     * 构建带上下文的 DataItem
     */
    private DataItem buildDataItem(long dataIndex, Scorer scorer, SumScoreStrategy strategy) {
        WorkflowContext ctx = new WorkflowContext();
        WorkflowContextOps.setScorerStrategy(ctx, strategy);
        WorkflowContextOps.setThreshold(ctx, 0.5);
        scorer.setWorkflowContext(ctx);

        DataItem dataItem = new DataItem();
        dataItem.setDataIndex(dataIndex);
        Map<String, Object> input = new HashMap<>();
        input.put("query", "测试查询");
        dataItem.setInputData(new InputData(dataIndex, input));
        ApiCompletionResult result = new ApiCompletionResult();
        result.setSuccess(true);
        Map<String, Object> res = new HashMap<>();
        res.put("response", "测试回复");
        result.setResultItem(res);
        dataItem.setApiCompletionResult(result);
        return dataItem;
    }

    // ─────────────────────────── calcScoreRate ───────────────────────────

    @Test
    void calcScoreRate_normalCase() {
        double rate = Scorer.calcScoreRate(0.8, 1.0);
        assertEquals(0.8, rate, 1e-6);
    }

    @Test
    void calcScoreRate_totalScoreIsZero_returnsZero() {
        double rate = Scorer.calcScoreRate(0.5, 0.0);
        assertEquals(0.0, rate, 1e-6);
    }

    @Test
    void calcScoreRate_fullScore() {
        double rate = Scorer.calcScoreRate(3.0, 3.0);
        assertEquals(1.0, rate, 1e-6);
    }

    @Test
    void calcScoreRate_zeroScore() {
        double rate = Scorer.calcScoreRate(0.0, 5.0);
        assertEquals(0.0, rate, 1e-6);
    }

    // ─────────────────────────── validConfig ─────────────────────────────

    @Test
    void validConfig_nullConfig_throwsIllegalArgument() {
        assertThatThrownBy(() -> buildScorer(null, 1, 0, false, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validConfig_negativeThreshold_throwsIllegalArgument() {
        assertThatThrownBy(() -> {
            ScorerConfig cfg = ScorerConfig.builder().metricName("m").threshold(-0.1).build();
            new Scorer(cfg) {
                @Override
                public ScorerResult eval(DataItem dataItem) {
                    return null;
                }
            };
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validConfig_zeroThreadNum_throwsIllegalArgument() {
        assertThatThrownBy(() -> {
            ScorerConfig cfg = ScorerConfig.builder().metricName("m").threadNum(0).build();
            new Scorer(cfg) {
                @Override
                public ScorerResult eval(DataItem dataItem) {
                    return null;
                }
            };
        }).isInstanceOf(IllegalArgumentException.class);
    }

    // ─────────────────────────── buildErrorResult ────────────────────────

    @Test
    void buildErrorResult_returnsFailedResult() {
        Scorer scorer = buildScorer("m", 1.0, 0, false, 1);
        DataItem item = new DataItem();
        item.setDataIndex(42L);
        RuntimeException ex = new RuntimeException("test error");

        ScorerResult result = scorer.buildErrorResult(item, ex);

        assertFalse(result.isSuccess());
        assertFalse(result.isPass());
        assertEquals(0, result.getScore(), 1e-6);
        assertEquals(42L, result.getDataIndex());
        assertTrue(result.getReason().contains("test error"));
    }

    // ─────────────────────────── evalWrapper ─────────────────────────────

    @Test
    void evalWrapper_normalEval_returnsCorrectResult() {
        Scorer scorer = buildScorer("准确率", 1.0, 0.5, false, 1.0);
        DataItem item = buildDataItem(1L, scorer, new SumScoreStrategy());

        ScorerResult result = scorer.evalWrapper(item);

        assertTrue(result.isSuccess());
        assertEquals(1.0, result.getScore(), 1e-6);
        assertEquals(1.0, result.getScoreRate(), 1e-6);
        assertEquals("准确率", result.getMetric());
    }

    @Test
    void evalWrapper_exceptionInEval_returnsErrorResult() {
        Scorer scorer = buildThrowingScorer("异常评估器");
        WorkflowContext ctx = new WorkflowContext();
        WorkflowContextOps.setScorerStrategy(ctx, new SumScoreStrategy());
        scorer.setWorkflowContext(ctx);
        DataItem item = new DataItem();
        item.setDataIndex(99L);
        item.setInputData(new InputData(99L, new HashMap<>()));

        ScorerResult result = scorer.evalWrapper(item);

        assertFalse(result.isSuccess());
        assertEquals(0, result.getScore(), 1e-6);
        assertTrue(result.getReason().contains("故意抛出的异常"));
    }

    // ─────────────────────────── decidePass (via evalWrapper) ─────────────

    @Test
    void decidePass_scoreValueStrategy_pass() {
        // SumScoreStrategy is ScoreValueStrategy, threshold=0.5, score=1.0 → pass
        Scorer scorer = buildScorer("m", 1.0, 0.5, false, 1.0);
        DataItem item = buildDataItem(1L, scorer, new SumScoreStrategy());

        ScorerResult result = scorer.evalWrapper(item);
        assertTrue(result.isPass());
    }

    @Test
    void decidePass_scoreValueStrategy_fail() {
        // threshold=0.9, score=0.5 → fail
        Scorer scorer = buildScorer("m", 1.0, 0.9, false, 0.5);
        DataItem item = buildDataItem(2L, scorer, new SumScoreStrategy());

        ScorerResult result = scorer.evalWrapper(item);
        assertFalse(result.isPass());
    }

    @Test
    void decidePass_scoreRateStrategy_pass() {
        // AvgScoreRateStrategy is ScoreRateStrategy, threshold=0.5, score=0.8/1.0=0.8 → pass
        Scorer scorer = buildScorer("m", 1.0, 0.5, false, 0.8);
        WorkflowContext ctx = new WorkflowContext();
        WorkflowContextOps.setScorerStrategy(ctx, new AvgScoreRateStrategy());
        scorer.setWorkflowContext(ctx);
        DataItem item = new DataItem();
        item.setDataIndex(3L);
        item.setInputData(new InputData(3L, new HashMap<>()));

        ScorerResult result = scorer.evalWrapper(item);
        assertTrue(result.isPass());
    }

    // ─────────────────────────── star field propagation ───────────────────

    @Test
    void evalWrapper_starFlag_propagatedToResult() {
        Scorer scorer = buildScorer("必过项", 1.0, 0.5, true, 1.0);
        DataItem item = buildDataItem(10L, scorer, new SumScoreStrategy());

        ScorerResult result = scorer.evalWrapper(item);
        assertTrue(result.isStar());
    }

    // ─────────────────────────── dynamicTotalScore ───────────────────────

    @Test
    void evalWrapper_dynamicTotalScore_usesResultTotalScore() {
        ScorerConfig cfg = ScorerConfig.builder()
                .metricName("动态总分")
                .totalScore(1.0)  // 配置总分1
                .dynamicTotalScore(true)
                .build();
        Scorer scorer = new Scorer(cfg) {
            @Override
            public ScorerResult eval(DataItem dataItem) {
                // 返回评估结果中的 totalScore=5，分数=4
                return new ScorerResult("动态总分", 4.0, 5.0, "理由");
            }
        };
        WorkflowContext ctx = new WorkflowContext();
        WorkflowContextOps.setScorerStrategy(ctx, new SumScoreStrategy());
        scorer.setWorkflowContext(ctx);
        DataItem item = new DataItem();
        item.setDataIndex(5L);
        item.setInputData(new InputData(5L, new HashMap<>()));

        ScorerResult result = scorer.evalWrapper(item);

        // totalScore 来自评估结果中的 5, scoreRate=4/5=0.8
        assertThat(result.getTotalScore()).isCloseTo(5.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(result.getScoreRate()).isCloseTo(0.8, org.assertj.core.data.Offset.offset(1e-6));
    }

    // ─────────────────────────── shouldEval（条件跳过）────────────────────

    @Test
    void shouldEval_nullCondition_alwaysTrue() {
        // condition=null 时，shouldEval 始终返回 true（向前兼容，不过滤任何数据项）
        Scorer scorer = buildScorer("m", 1.0, 0, false, 1.0);
        DataItem item = new DataItem();
        item.setDataIndex(1L);
        assertTrue(scorer.shouldEval(item));
    }

    @Test
    void shouldEval_conditionMatches_returnsTrue() {
        // condition 命中时返回 true，本 Scorer 正常执行
        ScorerConfig cfg = ScorerConfig.builder()
                .metricName("m")
                .condition(i -> "chat".equals(i.getInputData().get("scene")))
                .build();
        Scorer scorer = new Scorer(cfg) {
            @Override
            public ScorerResult eval(DataItem d) {
                return null;
            }
        };
        DataItem item = new DataItem();
        item.setInputData(new InputData(MapUtils.of("scene", "chat")));
        assertTrue(scorer.shouldEval(item));
    }

    @Test
    void shouldEval_conditionNotMatches_returnsFalse() {
        // condition 未命中时返回 false，doExecute 层将调用 buildSkipResult 跳过
        ScorerConfig cfg = ScorerConfig.builder()
                .metricName("m")
                .condition(i -> "chat".equals(i.getInputData().get("scene")))
                .build();
        Scorer scorer = new Scorer(cfg) {
            @Override
            public ScorerResult eval(DataItem d) {
                return null;
            }
        };
        DataItem item = new DataItem();
        item.setInputData(new InputData(MapUtils.of("scene", "search")));
        assertFalse(scorer.shouldEval(item));
    }

    @Test
    void shouldEval_conditionReturnsNull_treatedAsFalse() {
        // condition 返回 null 时视为 false，防止 NPE
        ScorerConfig cfg = ScorerConfig.builder()
                .metricName("m")
                .condition(i -> null)
                .build();
        Scorer scorer = new Scorer(cfg) {
            @Override
            public ScorerResult eval(DataItem d) {
                return null;
            }
        };
        assertFalse(scorer.shouldEval(new DataItem()));
    }

    // ─────────────────────────── buildSkipResult（跳过结果）──────────────

    @Test
    void buildSkipResult_fieldsCorrect() {
        // 跳过结果的各字段语义：success=true、pass=true（不拉低通过率），
        // totalScore=0（不影响汇总基准），reason 固定为 "skipped by condition"
        Scorer scorer = buildScorer("指标A", 1.0, 0.5, false, 1.0);
        DataItem item = new DataItem();
        item.setDataIndex(42L);

        ScorerResult skip = scorer.buildSkipResult(item);

        assertEquals(42L, skip.getDataIndex());
        assertEquals("指标A", skip.getMetric());
        assertEquals(0.0, skip.getScore(), 1e-6);
        assertEquals(0.0, skip.getTotalScore(), 1e-6);
        assertEquals("skipped by condition", skip.getReason());
        assertTrue(skip.isSuccess());
        assertTrue(skip.isPass());   // 跳过不算失败
    }

    @Test
    void buildSkipResult_starIsFalse_noVeto() {
        // 即使 config 中 star=true，跳过结果的 star 必须为 false，
        // 防止跳过的数据项触发一票否决逻辑
        ScorerConfig cfg = ScorerConfig.builder()
                .metricName("必过项")
                .star(true)
                .condition(i -> false)
                .build();
        Scorer scorer = new Scorer(cfg) {
            @Override
            public ScorerResult eval(DataItem d) {
                return null;
            }
        };
        DataItem item = new DataItem();
        item.setDataIndex(1L);

        assertFalse(scorer.buildSkipResult(item).isStar());
    }

    @Test
    void buildSkipResult_customSkipScore_writtenToResult() {
        // skipScore 配置的自定义值应写入跳过结果的 score 字段
        ScorerConfig cfg = ScorerConfig.builder()
                .metricName("m")
                .condition(i -> false)
                .skipScore(0.5)
                .build();
        Scorer scorer = new Scorer(cfg) {
            @Override
            public ScorerResult eval(DataItem d) {
                return null;
            }
        };
        DataItem item = new DataItem();
        item.setDataIndex(1L);

        assertEquals(0.5, scorer.buildSkipResult(item).getScore(), 1e-6);
    }

    @Test
    void buildSkipResult_scorerTypePreserved() {
        // 跳过结果应携带 scorerType，便于报告层区分来源
        Scorer scorer = buildScorer("m", 1.0, 0, false, 1.0);
        DataItem item = new DataItem();
        item.setDataIndex(1L);

        ScorerResult skip = scorer.buildSkipResult(item);
        assertNotNull(skip.getScorerType());
        assertFalse(skip.getScorerType().isEmpty());
    }
}