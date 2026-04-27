package com.evalkit.framework.eval.node.scorer.strategy;

import com.evalkit.framework.eval.model.ScorerResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 各评估分数策略单元测试
 * <p>
 * 覆盖: SumScoreStrategy / AvgScoreStrategy / MinScoreStrategy
 *       AvgScoreRateStrategy / MaxScoreRateStrategy / MinScoreRateStrategy / SumScoreRateStrategy
 */
class ScoreStrategyTest {

    // ─────────────── 辅助方法 ────────────────────────────────────────

    private ScorerResult r(double score, double scoreRate) {
        return ScorerResult.builder()
                .metric("m")
                .score(score)
                .scoreRate(scoreRate)
                .success(true)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // SumScoreStrategy
    // ═══════════════════════════════════════════════════════════════

    @Test
    void sumScore_normalCase() {
        SumScoreStrategy s = new SumScoreStrategy();
        List<ScorerResult> rs = Arrays.asList(r(0.8, 0.8), r(0.6, 0.6));
        assertEquals(1.4, s.calScore(rs), 1e-6);
    }

    @Test
    void sumScore_emptyList_returnsZero() {
        SumScoreStrategy s = new SumScoreStrategy();
        assertEquals(0.0, s.calScore(Collections.emptyList()), 1e-6);
    }

    @Test
    void sumScore_skipsFailedResults() {
        // SumScoreStrategy: 仅对 success=true 的结果求和
        ScorerResult failed = ScorerResult.builder().metric("f").score(0.9).success(false).build();
        ScorerResult passed = ScorerResult.builder().metric("p").score(1.0).success(true).build();
        SumScoreStrategy s = new SumScoreStrategy();
        // failed 不被计入（isSuccess=false 时不加）
        assertEquals(1.0, s.calScore(Arrays.asList(failed, passed)), 1e-6);
    }

    @Test
    void sumScore_strategyName() {
        assertEquals("分数求和策略", new SumScoreStrategy().getStrategyName());
    }

    // ═══════════════════════════════════════════════════════════════
    // AvgScoreStrategy
    // ═══════════════════════════════════════════════════════════════

    @Test
    void avgScore_normalCase() {
        AvgScoreStrategy s = new AvgScoreStrategy();
        List<ScorerResult> rs = Arrays.asList(r(0.8, 0.8), r(0.6, 0.6));
        assertEquals(0.7, s.calScore(rs), 1e-6);
    }

    @Test
    void avgScore_emptyList_returnsZero() {
        assertEquals(0.0, new AvgScoreStrategy().calScore(Collections.emptyList()), 1e-6);
    }

    @Test
    void avgScore_singleElement() {
        assertEquals(0.9, new AvgScoreStrategy().calScore(Collections.singletonList(r(0.9, 0.9))), 1e-6);
    }

    @Test
    void avgScore_skipsNegativeScore() {
        // score=-1 的结果被跳过
        AvgScoreStrategy s = new AvgScoreStrategy();
        List<ScorerResult> rs = Arrays.asList(r(1.0, 1.0), r(-1.0, 0.0));
        // 只有 score=1.0 有效 → 平均 = 1.0/1 = 1.0
        assertEquals(1.0, s.calScore(rs), 1e-6);
    }

    @Test
    void avgScore_strategyName() {
        assertEquals("平均分数策略", new AvgScoreStrategy().getStrategyName());
    }

    // ═══════════════════════════════════════════════════════════════
    // MinScoreStrategy
    // ═══════════════════════════════════════════════════════════════

    @Test
    void minScore_normalCase() {
        MinScoreStrategy s = new MinScoreStrategy();
        List<ScorerResult> rs = Arrays.asList(r(0.8, 0.8), r(0.3, 0.3), r(1.0, 1.0));
        assertEquals(0.3, s.calScore(rs), 1e-6);
    }

    @Test
    void minScore_emptyList_returnsZero() {
        assertEquals(0.0, new MinScoreStrategy().calScore(Collections.emptyList()), 1e-6);
    }

    @Test
    void minScore_singleElement() {
        assertEquals(0.7, new MinScoreStrategy().calScore(Collections.singletonList(r(0.7, 0.7))), 1e-6);
    }

    @Test
    void minScore_strategyName() {
        assertEquals("最小分数策略", new MinScoreStrategy().getStrategyName());
    }

    // ═══════════════════════════════════════════════════════════════
    // AvgScoreRateStrategy
    // ═══════════════════════════════════════════════════════════════

    @Test
    void avgScoreRate_normalCase() {
        AvgScoreRateStrategy s = new AvgScoreRateStrategy();
        // (0.8 + 0.6) / 2 = 0.7
        List<ScorerResult> rs = Arrays.asList(r(0.8, 0.8), r(0.6, 0.6));
        assertEquals(0.7, s.calScore(rs), 1e-6);
    }

    @Test
    void avgScoreRate_emptyList_returnsZero() {
        assertEquals(0.0, new AvgScoreRateStrategy().calScore(Collections.emptyList()), 1e-6);
    }

    @Test
    void avgScoreRate_singleElement() {
        assertEquals(0.5, new AvgScoreRateStrategy().calScore(Collections.singletonList(r(0.5, 0.5))), 1e-6);
    }

    @Test
    void avgScoreRate_strategyName() {
        assertEquals("平均得分率策略", new AvgScoreRateStrategy().getStrategyName());
    }

    // ═══════════════════════════════════════════════════════════════
    // MaxScoreRateStrategy
    // ═══════════════════════════════════════════════════════════════

    @Test
    void maxScoreRate_normalCase() {
        MaxScoreRateStrategy s = new MaxScoreRateStrategy();
        List<ScorerResult> rs = Arrays.asList(r(0.3, 0.3), r(0.9, 0.9), r(0.5, 0.5));
        assertEquals(0.9, s.calScore(rs), 1e-6);
    }

    @Test
    void maxScoreRate_emptyList_returnsZero() {
        assertEquals(0.0, new MaxScoreRateStrategy().calScore(Collections.emptyList()), 1e-6);
    }

    @Test
    void maxScoreRate_strategyName() {
        assertEquals("最大得分率策略", new MaxScoreRateStrategy().getStrategyName());
    }

    // ═══════════════════════════════════════════════════════════════
    // MinScoreRateStrategy
    // ═══════════════════════════════════════════════════════════════

    @Test
    void minScoreRate_emptyList_returnsZero() {
        assertEquals(0.0, new MinScoreRateStrategy().calScore(Collections.emptyList()), 1e-6);
    }

    @Test
    void minScoreRate_strategyName() {
        assertEquals("最小得分率策略", new MinScoreRateStrategy().getStrategyName());
    }

    // ═══════════════════════════════════════════════════════════════
    // SumScoreRateStrategy
    // ═══════════════════════════════════════════════════════════════

    @Test
    void sumScoreRate_normalCase() {
        SumScoreRateStrategy s = new SumScoreRateStrategy();
        List<ScorerResult> rs = Arrays.asList(r(0.5, 0.5), r(0.7, 0.7));
        assertEquals(1.2, s.calScore(rs), 1e-6);
    }

    @Test
    void sumScoreRate_emptyList_returnsZero() {
        assertEquals(0.0, new SumScoreRateStrategy().calScore(Collections.emptyList()), 1e-6);
    }

    @Test
    void sumScoreRate_strategyName() {
        assertEquals("得分率求和策略", new SumScoreRateStrategy().getStrategyName());
    }

    // ═══════════════════════════════════════════════════════════════
    // ScoreStrategy 类型判断
    // ═══════════════════════════════════════════════════════════════

    @Test
    void sumScore_isScoreValueStrategy() {
        assertTrue(new SumScoreStrategy() instanceof ScoreValueStrategy);
    }

    @Test
    void avgScore_isScoreValueStrategy() {
        assertTrue(new AvgScoreStrategy() instanceof ScoreValueStrategy);
    }

    @Test
    void minScore_isScoreValueStrategy() {
        assertTrue(new MinScoreStrategy() instanceof ScoreValueStrategy);
    }

    @Test
    void avgScoreRate_isScoreRateStrategy() {
        assertTrue(new AvgScoreRateStrategy() instanceof ScoreRateStrategy);
    }

    @Test
    void maxScoreRate_isScoreRateStrategy() {
        assertTrue(new MaxScoreRateStrategy() instanceof ScoreRateStrategy);
    }

    @Test
    void minScoreRate_isScoreRateStrategy() {
        assertTrue(new MinScoreRateStrategy() instanceof ScoreRateStrategy);
    }

    @Test
    void sumScoreRate_isScoreRateStrategy() {
        assertTrue(new SumScoreRateStrategy() instanceof ScoreRateStrategy);
    }
}

