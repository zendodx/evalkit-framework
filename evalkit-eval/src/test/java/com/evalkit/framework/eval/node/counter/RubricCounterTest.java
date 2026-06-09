package com.evalkit.framework.eval.node.counter;

import com.evalkit.framework.eval.model.*;
import com.evalkit.framework.eval.node.scorer.RubricBasedScorer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RubricCounterTest {

    private RubricCounter counter;

    @BeforeEach
    void setUp() {
        counter = new RubricCounter();
    }

    // ==================== 辅助构建方法 ====================

    /**
     * 构建一条带有 rubricBasedScorer 结果的 DataItem。
     *
     * @param dataIndex  样本序号
     * @param metricName 评估器名称
     * @param pass       整体是否通过
     * @param scoreRate  归一化得分率
     * @param rawScores  各维度原始分 {criteriaName -> rawScore}
     * @param normScores 各维度归一化分 {criteriaName -> normScore}
     * @param passRates  各维度通过阈值 {criteriaName -> passThreshold}
     * @param reasons    各维度打分理由 {criteriaName -> reason}
     */
    private DataItem buildRubricDataItem(long dataIndex,
                                         String metricName,
                                         boolean pass,
                                         double scoreRate,
                                         Map<String, Double> rawScores,
                                         Map<String, Double> normScores,
                                         Map<String, Double> passRates,
                                         Map<String, String> reasons) {
        DataItem item = new DataItem();
        item.setDataIndex(dataIndex);
        item.setInputData(new InputData(dataIndex, new HashMap<>()));

        ScorerResult sr = new ScorerResult();
        sr.setScorerType("rubricBasedScorer");
        sr.setMetric(metricName);
        sr.setPass(pass);
        sr.setScoreRate(scoreRate);
        sr.setScore(scoreRate);
        sr.addExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_RAW_SCORES, rawScores);
        sr.addExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_NORM_SCORES, normScores);
        sr.addExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_PASS_RATES, passRates);
        sr.addExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_REASONS, reasons);

        EvalResult evalResult = new EvalResult();
        evalResult.setPass(pass);
        evalResult.getScorerResults().add(sr);
        item.setEvalResult(evalResult);
        return item;
    }

    /**
     * 构建一个不含 rubricBasedScorer 类型的 DataItem（用于验证过滤逻辑）
     */
    private DataItem buildNonRubricDataItem(long dataIndex) {
        DataItem item = new DataItem();
        item.setDataIndex(dataIndex);
        ScorerResult sr = new ScorerResult();
        sr.setScorerType("basicScorer");
        sr.setMetric("other");
        EvalResult evalResult = new EvalResult();
        evalResult.getScorerResults().add(sr);
        item.setEvalResult(evalResult);
        return item;
    }

    // ==================== 边界：空/null/无 rubric 数据 ====================

    @Test
    void count_emptyList_returnsEmptyResult() {
        CountResult result = counter.count(Collections.emptyList());
        assertInstanceOf(RubricCountResult.class, result);
        assertTrue(((RubricCountResult) result).getMetricGroups().isEmpty());
    }

    @Test
    void count_nullEvalResult_skipped() {
        DataItem item = new DataItem();
        item.setDataIndex(1L);
        // evalResult 为 null，应被跳过
        CountResult result = counter.count(Collections.singletonList(item));
        assertTrue(((RubricCountResult) result).getMetricGroups().isEmpty());
    }

    @Test
    void count_nonRubricScorer_ignored() {
        List<DataItem> items = Arrays.asList(
                buildNonRubricDataItem(1),
                buildNonRubricDataItem(2)
        );
        RubricCountResult result = (RubricCountResult) counter.count(items);
        assertTrue(result.getMetricGroups().isEmpty(), "非 rubric 评估器结果应被过滤掉");
    }

    @Test
    void count_noScorerResults_skipped() {
        DataItem item = new DataItem();
        item.setDataIndex(1L);
        item.setEvalResult(new EvalResult());
        // scorerResults 为空列表
        RubricCountResult result = (RubricCountResult) counter.count(Collections.singletonList(item));
        assertTrue(result.getMetricGroups().isEmpty());
    }

    // ==================== 评估器级：计数与通过率 ====================

    @Test
    void count_singleMetric_allPass_rateIsOne() {
        Map<String, Double> raw = mapOf("Faithfulness", 5.0);
        Map<String, Double> norm = mapOf("Faithfulness", 1.0);
        Map<String, Double> rates = mapOf("Faithfulness", 0.6);
        Map<String, String> rsns = mapOf("Faithfulness", "很好");

        List<DataItem> items = Arrays.asList(
                buildRubricDataItem(1, "内容质量", true, 1.0, raw, norm, rates, rsns),
                buildRubricDataItem(2, "内容质量", true, 0.8, raw, norm, rates, rsns)
        );
        RubricCountResult result = (RubricCountResult) counter.count(items);

        assertEquals(1, result.getMetricGroups().size());
        RubricCountResult.RubricMetricGroup g = result.getMetricGroups().get(0);
        assertEquals("内容质量", g.getMetricName());
        assertEquals(2, g.getTotalCount());
        assertEquals(2, g.getPassCount());
        assertEquals(0, g.getFailCount());
        assertEquals(1.0, g.getPassRate(), 1e-6);
        assertEquals(0.0, g.getFailRate(), 1e-6);
    }

    @Test
    void count_singleMetric_halfPass() {
        Map<String, Double> raw = mapOf("Faithfulness", 3.0);
        Map<String, Double> norm = mapOf("Faithfulness", 0.5);
        Map<String, Double> rates = mapOf("Faithfulness", 0.6);
        Map<String, String> rsns = mapOf("Faithfulness", "一般");

        List<DataItem> items = Arrays.asList(
                buildRubricDataItem(1, "质量", true, 0.9, raw, norm, rates, rsns),
                buildRubricDataItem(2, "质量", false, 0.3, raw, norm, rates, rsns)
        );
        RubricCountResult result = (RubricCountResult) counter.count(items);

        RubricCountResult.RubricMetricGroup g = result.getMetricGroups().get(0);
        assertEquals(2, g.getTotalCount());
        assertEquals(1, g.getPassCount());
        assertEquals(1, g.getFailCount());
        assertEquals(0.5, g.getPassRate(), 1e-6);
        assertEquals(0.5, g.getFailRate(), 1e-6);
    }

    // ==================== 评估器级：分数统计 ====================

    @Test
    void count_scoreStats_avgMinMax() {
        // scoreRate: 0.4, 0.8, 1.0 → avg=0.7333, min=0.4, max=1.0
        Map<String, Double> raw = mapOf("C1", 1.0);
        Map<String, Double> norm = mapOf("C1", 1.0);
        Map<String, Double> rates = mapOf("C1", 0.5);
        Map<String, String> rsns = mapOf("C1", "ok");

        List<DataItem> items = Arrays.asList(
                buildRubricDataItem(1, "M", true, 0.4, raw, norm, rates, rsns),
                buildRubricDataItem(2, "M", true, 0.8, raw, norm, rates, rsns),
                buildRubricDataItem(3, "M", true, 1.0, raw, norm, rates, rsns)
        );
        RubricCountResult result = (RubricCountResult) counter.count(items);
        RubricCountResult.RubricMetricGroup g = result.getMetricGroups().get(0);

        assertEquals(0.4, g.getMinScore(), 1e-6);
        assertEquals(1.0, g.getMaxScore(), 1e-6);
        assertEquals((0.4 + 0.8 + 1.0) / 3, g.getAvgScore(), 1e-6);
    }

    @Test
    void count_scoreRate_zeroFallsBackToScore() {
        // scoreRate=0 时应降级使用 score=0.7
        Map<String, Double> raw = mapOf("C", 1.0);
        Map<String, Double> norm = mapOf("C", 1.0);
        Map<String, Double> rates = mapOf("C", 0.5);
        Map<String, String> rsns = mapOf("C", "");

        DataItem item = new DataItem();
        item.setDataIndex(1L);
        ScorerResult sr = new ScorerResult();
        sr.setScorerType("rubricBasedScorer");
        sr.setMetric("M");
        sr.setPass(true);
        sr.setScoreRate(0.0);   // 为 0，应降级
        sr.setScore(0.7);
        sr.addExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_RAW_SCORES, raw);
        sr.addExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_NORM_SCORES, norm);
        sr.addExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_PASS_RATES, rates);
        sr.addExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_REASONS, rsns);
        EvalResult er = new EvalResult();
        er.getScorerResults().add(sr);
        item.setEvalResult(er);

        RubricCountResult result = (RubricCountResult) counter.count(Collections.singletonList(item));
        assertEquals(0.7, result.getMetricGroups().get(0).getAvgScore(), 1e-6);
    }

    // ==================== 维度级：分组与通过率 ====================

    @Test
    void count_criteriaGroups_correctSize() {
        // 两个维度：Faithfulness, Harmfulness
        Map<String, Double> raw = new LinkedHashMap<>();
        raw.put("Faithfulness", 4.0);
        raw.put("Harmfulness", 0.0);

        Map<String, Double> norm = new LinkedHashMap<>();
        norm.put("Faithfulness", 0.8);
        norm.put("Harmfulness", 0.0);

        Map<String, Double> rates = new LinkedHashMap<>();
        rates.put("Faithfulness", 0.6);
        rates.put("Harmfulness", 0.5);

        Map<String, String> rsns = new LinkedHashMap<>();
        rsns.put("Faithfulness", "忠实");
        rsns.put("Harmfulness", "无害");

        List<DataItem> items = Arrays.asList(
                buildRubricDataItem(1, "RAG质量", true, 0.7, raw, norm, rates, rsns),
                buildRubricDataItem(2, "RAG质量", false, 0.2, raw, norm, rates, rsns)
        );
        RubricCountResult result = (RubricCountResult) counter.count(items);
        RubricCountResult.RubricMetricGroup g = result.getMetricGroups().get(0);
        assertEquals(2, g.getCriteriaGroups().size(), "应有两个维度分组");
    }

    @Test
    void count_criteriaPassRate_withThreshold() {
        // Faithfulness: norm=0.8, threshold=0.6 → 通过；norm=0.5 → 不通过
        Map<String, Double> rawPass = mapOf("Faithfulness", 4.0);
        Map<String, Double> normPass = mapOf("Faithfulness", 0.8);
        Map<String, Double> rawFail = mapOf("Faithfulness", 2.0);
        Map<String, Double> normFail = mapOf("Faithfulness", 0.4);
        Map<String, Double> rates = mapOf("Faithfulness", 0.6);
        Map<String, String> rsns = mapOf("Faithfulness", "测试");

        List<DataItem> items = Arrays.asList(
                buildRubricDataItem(1, "M", true, 0.8, rawPass, normPass, rates, rsns),
                buildRubricDataItem(2, "M", true, 0.8, rawPass, normPass, rates, rsns),
                buildRubricDataItem(3, "M", false, 0.4, rawFail, normFail, rates, rsns)
        );
        RubricCountResult result = (RubricCountResult) counter.count(items);
        RubricCountResult.CriteriaGroup cg = result.getMetricGroups().get(0)
                .getCriteriaGroups().get(0);

        assertEquals("Faithfulness", cg.getCriteriaName());
        assertEquals(0.6, cg.getPassThreshold(), 1e-6);
        assertEquals(2, cg.getPassCount());
        assertEquals(1, cg.getFailCount());
        assertEquals(2.0 / 3, cg.getPassRate(), 1e-6);
        assertEquals(1.0 / 3, cg.getFailRate(), 1e-6);
    }

    @Test
    void count_criteriaAvgScores() {
        // rawScores: 3.0, 5.0 → avg=4.0；normScores: 0.6, 1.0 → avg=0.8
        Map<String, Double> raw1 = mapOf("C", 3.0);
        Map<String, Double> norm1 = mapOf("C", 0.6);
        Map<String, Double> raw2 = mapOf("C", 5.0);
        Map<String, Double> norm2 = mapOf("C", 1.0);
        Map<String, Double> rates = mapOf("C", 0.6);
        Map<String, String> rsns = mapOf("C", "");

        List<DataItem> items = Arrays.asList(
                buildRubricDataItem(1, "M", true, 0.6, raw1, norm1, rates, rsns),
                buildRubricDataItem(2, "M", true, 1.0, raw2, norm2, rates, rsns)
        );
        RubricCountResult result = (RubricCountResult) counter.count(items);
        RubricCountResult.CriteriaGroup cg = result.getMetricGroups().get(0).getCriteriaGroups().get(0);

        assertEquals(4.0, cg.getAvgRawScore(), 1e-6);
        assertEquals(0.8, cg.getAvgNormScore(), 1e-6);
    }

    // ==================== DataPoint（样本明细） ====================

    @Test
    void count_dataPoints_correctCount() {
        Map<String, Double> raw = mapOf("C", 1.0);
        Map<String, Double> norm = mapOf("C", 1.0);
        Map<String, Double> rates = mapOf("C", 0.5);
        Map<String, String> rsns = mapOf("C", "good");

        List<DataItem> items = Arrays.asList(
                buildRubricDataItem(10, "M", true, 1.0, raw, norm, rates, rsns),
                buildRubricDataItem(20, "M", false, 0.3, raw, norm, rates, rsns),
                buildRubricDataItem(30, "M", true, 0.9, raw, norm, rates, rsns)
        );
        RubricCountResult result = (RubricCountResult) counter.count(items);
        List<RubricCountResult.CriteriaDataPoint> dps =
                result.getMetricGroups().get(0).getCriteriaGroups().get(0).getDataPoints();

        assertEquals(3, dps.size());
    }

    @Test
    void count_dataPoints_indexAndPassedCorrect() {
        // item1: normScore=1.0 >= threshold=0.6 → passed=true
        // item2: normScore=0.4 < threshold=0.6  → passed=false
        Map<String, Double> raw1 = mapOf("F", 5.0);
        Map<String, Double> norm1 = mapOf("F", 1.0);
        Map<String, Double> raw2 = mapOf("F", 2.0);
        Map<String, Double> norm2 = mapOf("F", 0.4);
        Map<String, Double> rates = mapOf("F", 0.6);
        Map<String, String> rsns = mapOf("F", "理由");

        List<DataItem> items = Arrays.asList(
                buildRubricDataItem(1, "M", true, 1.0, raw1, norm1, rates, rsns),
                buildRubricDataItem(2, "M", false, 0.4, raw2, norm2, rates, rsns)
        );
        RubricCountResult result = (RubricCountResult) counter.count(items);
        List<RubricCountResult.CriteriaDataPoint> dps =
                result.getMetricGroups().get(0).getCriteriaGroups().get(0).getDataPoints();

        assertEquals(2, dps.size());
        // 验证 dataIndex
        Set<Long> indices = new HashSet<>();
        dps.forEach(dp -> indices.add(dp.getDataIndex()));
        assertTrue(indices.contains(1L));
        assertTrue(indices.contains(2L));

        // 验证 passed 字段
        Map<Long, Boolean> passedMap = new HashMap<>();
        dps.forEach(dp -> passedMap.put(dp.getDataIndex(), dp.isPassed()));
        assertTrue(passedMap.get(1L), "dataIndex=1: normScore=1.0 >= threshold=0.6, 应通过");
        assertFalse(passedMap.get(2L), "dataIndex=2: normScore=0.4 < threshold=0.6,  不应通过");
    }

    @Test
    void count_dataPoints_reasonPreserved() {
        Map<String, Double> raw = mapOf("C", 1.0);
        Map<String, Double> norm = mapOf("C", 1.0);
        Map<String, Double> rates = mapOf("C", 0.5);
        Map<String, String> rsns = mapOf("C", "这是打分理由");

        List<DataItem> items = Collections.singletonList(
                buildRubricDataItem(5, "M", true, 1.0, raw, norm, rates, rsns)
        );
        RubricCountResult result = (RubricCountResult) counter.count(items);
        RubricCountResult.CriteriaDataPoint dp =
                result.getMetricGroups().get(0).getCriteriaGroups().get(0).getDataPoints().get(0);

        assertEquals("这是打分理由", dp.getReason());
    }

    // ==================== 多评估器分组 ====================

    @Test
    void count_multiMetric_separateGroups() {
        Map<String, Double> raw = mapOf("C", 1.0);
        Map<String, Double> norm = mapOf("C", 1.0);
        Map<String, Double> rates = mapOf("C", 0.5);
        Map<String, String> rsns = mapOf("C", "");

        List<DataItem> items = Arrays.asList(
                buildRubricDataItem(1, "MetricA", true, 1.0, raw, norm, rates, rsns),
                buildRubricDataItem(2, "MetricB", false, 0.2, raw, norm, rates, rsns),
                buildRubricDataItem(3, "MetricA", true, 0.9, raw, norm, rates, rsns)
        );
        RubricCountResult result = (RubricCountResult) counter.count(items);

        assertEquals(2, result.getMetricGroups().size(), "应有两个评估器分组");

        Map<String, RubricCountResult.RubricMetricGroup> groupMap = new HashMap<>();
        result.getMetricGroups().forEach(g -> groupMap.put(g.getMetricName(), g));

        assertTrue(groupMap.containsKey("MetricA"));
        assertTrue(groupMap.containsKey("MetricB"));
        assertEquals(2, groupMap.get("MetricA").getTotalCount());
        assertEquals(1, groupMap.get("MetricB").getTotalCount());
    }

    // ==================== rubric 与非 rubric 混合 ====================

    @Test
    void count_mixedScorerTypes_onlyRubricCounted() {
        Map<String, Double> raw = mapOf("C", 1.0);
        Map<String, Double> norm = mapOf("C", 1.0);
        Map<String, Double> rates = mapOf("C", 0.5);
        Map<String, String> rsns = mapOf("C", "");

        DataItem rubricItem = buildRubricDataItem(1, "Q", true, 1.0, raw, norm, rates, rsns);
        DataItem nonRubric = buildNonRubricDataItem(2);

        RubricCountResult result = (RubricCountResult) counter.count(Arrays.asList(rubricItem, nonRubric));

        assertEquals(1, result.getMetricGroups().size());
        assertEquals("Q", result.getMetricGroups().get(0).getMetricName());
    }

    @Test
    void count_sameDataItemHasRubricAndNonRubricScorer_onlyRubricExtracted() {
        Map<String, Double> raw = mapOf("C", 1.0);
        Map<String, Double> norm = mapOf("C", 1.0);
        Map<String, Double> rates = mapOf("C", 0.5);
        Map<String, String> rsns = mapOf("C", "");

        // 同一 DataItem 里有两个 ScorerResult：一个 rubric，一个普通
        DataItem item = new DataItem();
        item.setDataIndex(1L);

        ScorerResult rubricSr = new ScorerResult();
        rubricSr.setScorerType("rubricBasedScorer");
        rubricSr.setMetric("RubricMetric");
        rubricSr.setPass(true);
        rubricSr.setScoreRate(1.0);
        rubricSr.addExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_RAW_SCORES, raw);
        rubricSr.addExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_NORM_SCORES, norm);
        rubricSr.addExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_PASS_RATES, rates);
        rubricSr.addExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_REASONS, rsns);

        ScorerResult otherSr = new ScorerResult();
        otherSr.setScorerType("basicScorer");
        otherSr.setMetric("OtherMetric");

        EvalResult er = new EvalResult();
        er.getScorerResults().add(rubricSr);
        er.getScorerResults().add(otherSr);
        item.setEvalResult(er);

        RubricCountResult result = (RubricCountResult) counter.count(Collections.singletonList(item));

        assertEquals(1, result.getMetricGroups().size());
        assertEquals("RubricMetric", result.getMetricGroups().get(0).getMetricName());
    }

    // ==================== extra 字段缺失的容错 ====================

    @Test
    void count_missingNormScores_fallsBackToRaw() {
        // normScores 为 null，应降级使用 rawScore 填充 normScore
        DataItem item = new DataItem();
        item.setDataIndex(1L);
        ScorerResult sr = new ScorerResult();
        sr.setScorerType("rubricBasedScorer");
        sr.setMetric("M");
        sr.setPass(true);
        sr.setScoreRate(0.8);
        sr.addExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_RAW_SCORES, mapOf("C", 4.0));
        // 故意不设置 EXTRA_KEY_CRITERIA_NORM_SCORES
        sr.addExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_PASS_RATES, mapOf("C", 0.6));
        sr.addExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_REASONS, mapOf("C", "ok"));
        EvalResult er = new EvalResult();
        er.getScorerResults().add(sr);
        item.setEvalResult(er);

        RubricCountResult result = (RubricCountResult) counter.count(Collections.singletonList(item));
        RubricCountResult.CriteriaGroup cg = result.getMetricGroups().get(0).getCriteriaGroups().get(0);

        // normScore 降级为 rawScore=4.0
        assertEquals(4.0, cg.getAvgNormScore(), 1e-6);
    }

    @Test
    void count_missingRawScores_criteriaSkipped() {
        // rawScores 为 null，该 ScorerResult 的维度数据应被跳过
        DataItem item = new DataItem();
        item.setDataIndex(1L);
        ScorerResult sr = new ScorerResult();
        sr.setScorerType("rubricBasedScorer");
        sr.setMetric("M");
        sr.setPass(true);
        sr.setScoreRate(0.9);
        // 故意不设置任何 extra（rawScores 为 null）
        EvalResult er = new EvalResult();
        er.getScorerResults().add(sr);
        item.setEvalResult(er);

        RubricCountResult result = (RubricCountResult) counter.count(Collections.singletonList(item));
        // 评估器分组存在，但无维度数据
        assertEquals(1, result.getMetricGroups().size());
        assertTrue(result.getMetricGroups().get(0).getCriteriaGroups().isEmpty(),
                "rawScores 为 null 时，维度分组应为空");
    }

    @Test
    void count_nullMetricName_groupedAsUnknown() {
        Map<String, Double> raw = mapOf("C", 1.0);
        Map<String, Double> norm = mapOf("C", 1.0);
        Map<String, Double> rates = mapOf("C", 0.5);
        Map<String, String> rsns = mapOf("C", "");

        DataItem item = new DataItem();
        item.setDataIndex(1L);
        ScorerResult sr = new ScorerResult();
        sr.setScorerType("rubricBasedScorer");
        sr.setMetric(null);   // metric 为 null
        sr.setPass(true);
        sr.setScoreRate(1.0);
        sr.addExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_RAW_SCORES, raw);
        sr.addExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_NORM_SCORES, norm);
        sr.addExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_PASS_RATES, rates);
        sr.addExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_REASONS, rsns);
        EvalResult er = new EvalResult();
        er.getScorerResults().add(sr);
        item.setEvalResult(er);

        RubricCountResult result = (RubricCountResult) counter.count(Collections.singletonList(item));
        assertEquals(1, result.getMetricGroups().size());
        assertEquals("unknown", result.getMetricGroups().get(0).getMetricName(),
                "metric 为 null 时应归入 'unknown' 分组");
    }

    // ==================== counterName ====================

    @Test
    void countResult_counterName() {
        RubricCountResult r = new RubricCountResult();
        assertEquals("rubricCountResult", r.counterName());
    }

    // ==================== 辅助工具 ====================

    private static <K, V> Map<K, V> mapOf(K key, V value) {
        Map<K, V> m = new LinkedHashMap<>();
        m.put(key, value);
        return m;
    }
}