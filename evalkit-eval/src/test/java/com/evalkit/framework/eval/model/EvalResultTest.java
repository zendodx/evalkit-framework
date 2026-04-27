package com.evalkit.framework.eval.model;

import com.evalkit.framework.eval.node.scorer.strategy.AvgScoreRateStrategy;
import com.evalkit.framework.eval.node.scorer.strategy.NormalEvalReasonStrategy;
import com.evalkit.framework.eval.node.scorer.strategy.SumScoreStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EvalResult 核心逻辑单元测试
 */
class EvalResultTest {

    // ─────────────── 辅助方法 ────────────────────────────────────────

    private ScorerResult buildResult(String metric, double score, double totalScore,
                                     double scoreRate, boolean pass, boolean success,
                                     boolean star) {
        return ScorerResult.builder()
                .dataIndex(1L)
                .metric(metric)
                .score(score)
                .totalScore(totalScore)
                .scoreRate(scoreRate)
                .reason("测试理由")
                .pass(pass)
                .success(success)
                .star(star)
                .statTime(System.currentTimeMillis())
                .endTime(System.currentTimeMillis() + 10)
                .timeCost(10L)
                .build();
    }

    // ─────────────── 默认构造器 ───────────────────────────────────────

    @Test
    void defaultConstructor_hasEmptyScorerResults() {
        EvalResult evalResult = new EvalResult();
        assertNotNull(evalResult.getScorerResults());
        assertTrue(evalResult.getScorerResults().isEmpty());
        assertEquals(0.0, evalResult.getScore(), 1e-6);
    }

    // ─────────────── addScorerResult: 分数累加 ───────────────────────

    @Test
    void addScorerResult_sumStrategy_accumulatesScore() {
        EvalResult result = new EvalResult(
                new java.util.concurrent.CopyOnWriteArrayList<>(),
                new SumScoreStrategy(),
                0.5,
                new NormalEvalReasonStrategy()
        );
        result.addScorerResult(buildResult("指标1", 0.8, 1.0, 0.8, true, true, false));
        result.addScorerResult(buildResult("指标2", 0.6, 1.0, 0.6, true, true, false));

        assertEquals(1.4, result.getScore(), 1e-6);
        assertEquals(2, result.getScorerResults().size());
    }

    // ─────────────── updatePass: 分数阈值 ────────────────────────────

    @Test
    void updatePass_aboveThreshold_pass() {
        EvalResult result = new EvalResult(
                new java.util.concurrent.CopyOnWriteArrayList<>(),
                new SumScoreStrategy(),
                1.0,  // threshold = 1.0
                new NormalEvalReasonStrategy()
        );
        result.addScorerResult(buildResult("m", 1.0, 1.0, 1.0, true, true, false));
        assertTrue(result.isPass());
    }

    @Test
    void updatePass_belowThreshold_fail() {
        EvalResult result = new EvalResult(
                new java.util.concurrent.CopyOnWriteArrayList<>(),
                new SumScoreStrategy(),
                1.5,  // threshold = 1.5
                new NormalEvalReasonStrategy()
        );
        result.addScorerResult(buildResult("m", 1.0, 1.0, 1.0, true, true, false));
        assertFalse(result.isPass());
    }

    // ─────────────── updatePass: star(必过) 拦截 ─────────────────────

    @Test
    void updatePass_starCheckerFail_overallFail() {
        EvalResult result = new EvalResult(
                new java.util.concurrent.CopyOnWriteArrayList<>(),
                new SumScoreStrategy(),
                0.0,  // threshold=0，其他都会 pass
                new NormalEvalReasonStrategy()
        );
        // 必过项没通过
        result.addScorerResult(buildResult("必过项", 0.0, 1.0, 0.0, false, true, true));
        // 普通项通过
        result.addScorerResult(buildResult("普通项", 1.0, 1.0, 1.0, true, true, false));

        assertFalse(result.isPass(), "star 指标未通过时整体结果应为 fail");
    }

    @Test
    void updatePass_starCheckerPass_normalDetermines() {
        EvalResult result = new EvalResult(
                new java.util.concurrent.CopyOnWriteArrayList<>(),
                new SumScoreStrategy(),
                1.5,  // threshold = 1.5，分数之和需 >= 1.5
                new NormalEvalReasonStrategy()
        );
        // 必过项通过
        result.addScorerResult(buildResult("必过项", 1.0, 1.0, 1.0, true, true, true));
        // 普通项通过
        result.addScorerResult(buildResult("普通项", 0.8, 1.0, 0.8, true, true, false));

        // 1.0 + 0.8 = 1.8 >= 1.5
        assertTrue(result.isPass());
    }

    // ─────────────── success: 全部 success 时 evalResult success ───────

    @Test
    void success_allScorerResultsSuccess_resultSuccess() {
        EvalResult result = new EvalResult();
        result.addScorerResult(buildResult("m1", 1.0, 1.0, 1.0, true, true, false));
        result.addScorerResult(buildResult("m2", 1.0, 1.0, 1.0, true, true, false));
        assertTrue(result.isSuccess());
    }

    @Test
    void success_allScorersFailed_resultNotSuccess() {
        // 当只有一条 success=false 的结果时，EvalResult.success 应为 false
        EvalResult result = new EvalResult();
        result.addScorerResult(buildResult("m1", 0.0, 1.0, 0.0, false, false, false));
        assertFalse(result.isSuccess());
    }

    // ─────────────── timeCost 计算 ────────────────────────────────────

    @Test
    void timeCost_isMaxOfAllScorerTimeCosts() {
        EvalResult result = new EvalResult();
        ScorerResult r1 = buildResult("m1", 1.0, 1.0, 1.0, true, true, false);
        ScorerResult r2 = buildResult("m2", 1.0, 1.0, 1.0, true, true, false);
        r1.setTimeCost(100L);
        r2.setTimeCost(300L);
        result.addScorerResult(r1);
        result.addScorerResult(r2);

        assertEquals(300L, result.getTimeCost());
    }

    // ─────────────── reason 构建 ─────────────────────────────────────

    @Test
    void reason_isBuiltFromEvalReasonStrategy() {
        EvalResult result = new EvalResult(
                new java.util.concurrent.CopyOnWriteArrayList<>(),
                new SumScoreStrategy(),
                0.0,
                new NormalEvalReasonStrategy()
        );
        result.addScorerResult(buildResult("m", 1.0, 1.0, 1.0, true, true, false));
        assertNotNull(result.getReason());
    }

    // ─────────────── avgScoreRateStrategy ────────────────────────────

    @Test
    void avgScoreRateStrategy_calScore() {
        EvalResult result = new EvalResult(
                new java.util.concurrent.CopyOnWriteArrayList<>(),
                new AvgScoreRateStrategy(),
                0.0,
                new NormalEvalReasonStrategy()
        );
        result.addScorerResult(buildResult("m1", 0.8, 1.0, 0.8, true, true, false));
        result.addScorerResult(buildResult("m2", 0.6, 1.0, 0.6, true, true, false));

        // AvgScoreRateStrategy 计算 (0.8 + 0.6) / 2 = 0.7
        assertEquals(0.7, result.getScore(), 1e-6);
    }

    // ─────────────── 并发安全 (smoke test) ───────────────────────────

    @Test
    void concurrentAddScorerResult_doesNotThrow() throws InterruptedException {
        EvalResult result = new EvalResult();
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> {
                ScorerResult r = buildResult("m" + idx, 1.0, 1.0, 1.0, true, true, false);
                r.setDataIndex((long) idx);
                result.addScorerResult(r);
            });
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        assertEquals(threadCount, result.getScorerResults().size());
    }
}

