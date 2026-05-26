package com.evalkit.framework.eval.node.counter;

import com.evalkit.framework.eval.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BasicCounterTest {

    private BasicCounter counter;

    @BeforeEach
    void setUp() {
        counter = new BasicCounter();
    }

    // ─────────────────────── 辅助方法 ─────────────────────────────────

    /** 构建一个已有 EvalResult 的 DataItem */
    private DataItem buildDataItem(long idx, boolean pass, boolean evalSuccess,
                                   boolean completionSuccess, double score,
                                   long completionTimeCost) {
        DataItem item = new DataItem();
        item.setDataIndex(idx);
        item.setInputData(new InputData(idx, new HashMap<>()));

        // ApiCompletionResult
        ApiCompletionResult completionResult = new ApiCompletionResult();
        completionResult.setSuccess(completionSuccess);
        completionResult.setTimeCost(completionTimeCost);
        item.setApiCompletionResult(completionResult);

        // EvalResult
        EvalResult evalResult = new EvalResult();
        evalResult.setPass(pass);
        evalResult.setSuccess(evalSuccess);
        evalResult.setScore(score);
        evalResult.setTimeCost(200L);
        item.setEvalResult(evalResult);

        return item;
    }

    // ─────────────────────── calRate ──────────────────────────────────

    @Test
    void calRate_allPass() {
        List<DataItem> items = Arrays.asList(
                buildDataItem(1, true, true, true, 1.0, 100),
                buildDataItem(2, true, true, true, 1.0, 100)
        );
        BasicCountResult result = new BasicCountResult();
        counter.calRate(items, result);

        assertEquals(2, result.getTotalCount());
        assertEquals(2, result.getPassCount());
        assertEquals(0, result.getUnPassCount());
        assertEquals(1.0, result.getPassRate(), 1e-6);
        assertEquals(0.0, result.getUnPassRate(), 1e-6);
    }

    @Test
    void calRate_halfPass() {
        List<DataItem> items = Arrays.asList(
                buildDataItem(1, true, true, true, 1.0, 100),
                buildDataItem(2, false, true, true, 0.0, 100)
        );
        BasicCountResult result = new BasicCountResult();
        counter.calRate(items, result);

        assertEquals(2, result.getTotalCount());
        assertEquals(1, result.getPassCount());
        assertEquals(1, result.getUnPassCount());
        assertEquals(0.5, result.getPassRate(), 1e-6);
        assertEquals(0.5, result.getUnPassRate(), 1e-6);
    }

    @Test
    void calRate_evalError() {
        List<DataItem> items = Arrays.asList(
                buildDataItem(1, false, false, true, 0.0, 100)
        );
        BasicCountResult result = new BasicCountResult();
        counter.calRate(items, result);

        assertEquals(1, result.getEvalErrorCount());
        assertEquals(0, result.getEvalSuccessCount());
        assertEquals(1.0, result.getEvalErrorRate(), 1e-6);
    }

    @Test
    void calRate_completionError() {
        List<DataItem> items = Arrays.asList(
                buildDataItem(1, false, true, false, 0.0, 0)
        );
        BasicCountResult result = new BasicCountResult();
        counter.calRate(items, result);

        assertEquals(1, result.getCompletionErrorCount());
        assertEquals(0, result.getCompletionSuccessCount());
        assertEquals(1.0, result.getCompletionErrorRate(), 1e-6);
    }

    @Test
    void calRate_emptyList_doesNothing() {
        BasicCountResult result = new BasicCountResult();
        counter.calRate(Collections.emptyList(), result);
        assertEquals(0, result.getTotalCount());
    }

    // ─────────────────────── calApiCompletionTimeCost ─────────────────

    @Test
    void calApiCompletionTimeCost_normal() {
        List<DataItem> items = Arrays.asList(
                buildDataItem(1, true, true, true, 1.0, 100),
                buildDataItem(2, true, true, true, 1.0, 200),
                buildDataItem(3, true, true, true, 1.0, 300)
        );
        BasicCountResult result = new BasicCountResult();
        counter.calApiCompletionTimeCost(items, result);

        assertEquals(200.0, result.getCompletionAvgTimeCost(), 1e-6);
        assertEquals(100L, result.getCompletionMinTimeCost());
        assertEquals(300L, result.getCompletionMaxTimeCost());
    }

    @Test
    void calApiCompletionTimeCost_noSuccessCompletion_skips() {
        List<DataItem> items = Arrays.asList(
                buildDataItem(1, false, false, false, 0.0, 500)
        );
        BasicCountResult result = new BasicCountResult();
        counter.calApiCompletionTimeCost(items, result);

        // 没有成功的调用，不更新任何字段
        assertEquals(0, result.getCompletionMinTimeCost());
        assertEquals(0, result.getCompletionMaxTimeCost());
    }

    // ─────────────────────── calEvalTimeCost ─────────────────────────

    @Test
    void calEvalTimeCost_normal() {
        DataItem item = buildDataItem(1, true, true, true, 1.0, 100);
        item.getEvalResult().setTimeCost(500L);
        List<DataItem> items = Collections.singletonList(item);

        BasicCountResult result = new BasicCountResult();
        counter.calEvalTimeCost(items, result);

        assertEquals(500.0, result.getEvalAvgTimeCost(), 1e-6);
        assertEquals(500L, result.getEvalMinTimeCost());
        assertEquals(500L, result.getEvalMaxTimeCost());
    }

    // ─────────────────────── count (整体) ──────────────────────────────

    @Test
    void count_integratesAll() {
        List<DataItem> items = Arrays.asList(
                buildDataItem(1, true, true, true, 0.9, 150),
                buildDataItem(2, false, true, true, 0.4, 200),
                buildDataItem(3, true, true, false, 0.8, 0)
        );
        // 触发 count (通过 doExecute 不易测试，直接调用 public calRate 等方法组合)
        BasicCountResult result = new BasicCountResult();
        counter.calRate(items, result);

        assertEquals(3, result.getTotalCount());
        assertEquals(2, result.getPassCount());
        assertEquals(1, result.getUnPassCount());
    }
}