package com.evalkit.framework.eval.node.counter;

import com.evalkit.framework.eval.model.*;
import com.evalkit.framework.eval.node.scorer.RubricBasedScorer;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;

/**
 * Rubric 评估器指标统计器。
 * <p>
 * 从所有 {@link DataItem} 中提取 {@link RubricBasedScorer}（及其子类）的评估结果，
 * 按「评估器 → 维度」两级聚合，输出 {@link RubricCountResult}：
 * <ul>
 *   <li><b>评估器级</b>：totalCount / passCount / failCount / passRate / avgScore / minScore / maxScore</li>
 *   <li><b>维度级</b>：avgRawScore / avgNormScore / passThreshold / passCount / failCount / passRate
 *       及每条样本的打分明细（dataPoints，用于报告层下钻）</li>
 * </ul>
 *
 * <p>识别 RubricBasedScorer 的方式：{@code ScorerResult.scorerType == "rubricBasedScorer"}
 */
public class RubricCounter extends Counter {

    public RubricCounter() {
        super.counterType = "rubricCounter";
    }

    @Override
    protected CountResult count(List<DataItem> dataItems) {
        RubricCountResult result = new RubricCountResult();
        if (CollectionUtils.isEmpty(dataItems)) {
            return result;
        }

        // ── Step 1: 收集所有 rubric ScorerResult，按 metric 分组 ────────────────
        // key: metricName → value: list of (dataIndex, scorerResult)
        Map<String, List<Map.Entry<Long, ScorerResult>>> metricMap = new LinkedHashMap<>();

        for (DataItem item : dataItems) {
            EvalResult evalResult = item.getEvalResult();
            if (evalResult == null || CollectionUtils.isEmpty(evalResult.getScorerResults())) {
                continue;
            }
            Long dataIndex = item.getDataIndex();
            for (ScorerResult sr : evalResult.getScorerResults()) {
                if (!"rubricBasedScorer".equals(sr.getScorerType())) {
                    continue;
                }
                String metricName = sr.getMetric();
                if (metricName == null) metricName = "unknown";
                metricMap
                        .computeIfAbsent(metricName, k -> new ArrayList<>())
                        .add(new AbstractMap.SimpleEntry<>(dataIndex, sr));
            }
        }

        if (metricMap.isEmpty()) {
            return result;
        }

        // ── Step 2: 逐评估器聚合 ─────────────────────────────────────────────────
        List<RubricCountResult.RubricMetricGroup> metricGroups = new ArrayList<>();

        for (Map.Entry<String, List<Map.Entry<Long, ScorerResult>>> metricEntry : metricMap.entrySet()) {
            String metricName = metricEntry.getKey();
            List<Map.Entry<Long, ScorerResult>> srList = metricEntry.getValue();

            RubricCountResult.RubricMetricGroup metricGroup = new RubricCountResult.RubricMetricGroup();
            metricGroup.setMetricName(metricName);
            metricGroup.setTotalCount(srList.size());

            // 评估器级：pass/fail、分数统计
            int passCount = 0;
            int failCount = 0;
            List<Double> finalScores = new ArrayList<>();

            for (Map.Entry<Long, ScorerResult> e : srList) {
                ScorerResult sr = e.getValue();
                if (sr.isPass()) passCount++;
                else failCount++;
                // 使用 scoreRate（归一化后的最终得分率），若为 0 则降级使用 score
                double s = sr.getScoreRate() > 0 ? sr.getScoreRate() : sr.getScore();
                finalScores.add(s);
            }

            metricGroup.setPassCount(passCount);
            metricGroup.setFailCount(failCount);
            metricGroup.setPassRate(srList.size() > 0 ? (double) passCount / srList.size() : 0);
            metricGroup.setFailRate(srList.size() > 0 ? (double) failCount / srList.size() : 0);

            if (!finalScores.isEmpty()) {
                metricGroup.setAvgScore(finalScores.stream().mapToDouble(Double::doubleValue).average().orElse(0));
                metricGroup.setMinScore(finalScores.stream().mapToDouble(Double::doubleValue).min().orElse(0));
                metricGroup.setMaxScore(finalScores.stream().mapToDouble(Double::doubleValue).max().orElse(0));
            }

            // ── Step 3: 维度级聚合 ────────────────────────────────────────────
            // 从 extra 中收集各维度原始分、归一化分、通过阈值、理由
            // key: criteriaName → list of (dataIndex, rawScore, normScore, passThreshold, reason)
            Map<String, List<CriteriaRawRecord>> criteriaMap = new LinkedHashMap<>();

            for (Map.Entry<Long, ScorerResult> e : srList) {
                Long dataIndex = e.getKey();
                ScorerResult sr = e.getValue();

                Map<String, Double> rawScores = sr.getExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_RAW_SCORES);
                Map<String, Double> normScores = sr.getExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_NORM_SCORES);
                Map<String, Double> passRates = sr.getExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_PASS_RATES);
                Map<String, String> reasons = sr.getExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_REASONS);
                Map<String, String> scoringGuides = sr.getExtraItem(RubricBasedScorer.EXTRA_KEY_CRITERIA_SCORING_GUIDES);

                if (rawScores == null) continue;

                for (String criteriaName : rawScores.keySet()) {
                    double rawScore = rawScores.getOrDefault(criteriaName, 0.0);
                    double normScore = normScores != null ? normScores.getOrDefault(criteriaName, 0.0) : rawScore;
                    double passThreshold = passRates != null ? passRates.getOrDefault(criteriaName, 1.0) : 1.0;
                    String reason = reasons != null ? reasons.getOrDefault(criteriaName, "") : "";
                    String scoringGuide = scoringGuides != null ? scoringGuides.getOrDefault(criteriaName, null) : null;

                    criteriaMap
                            .computeIfAbsent(criteriaName, k -> new ArrayList<>())
                            .add(new CriteriaRawRecord(dataIndex, rawScore, normScore, passThreshold, reason, scoringGuide));
                }
            }

            // 逐维度聚合
            List<RubricCountResult.CriteriaGroup> criteriaGroups = new ArrayList<>();
            for (Map.Entry<String, List<CriteriaRawRecord>> ce : criteriaMap.entrySet()) {
                String criteriaName = ce.getKey();
                List<CriteriaRawRecord> records = ce.getValue();

                RubricCountResult.CriteriaGroup cg = new RubricCountResult.CriteriaGroup();
                cg.setCriteriaName(criteriaName);

                // 取第一条记录的 passThreshold 和 scoringGuide（同一维度应相同）
                double passThreshold = records.isEmpty() ? 1.0 : records.get(0).passThreshold;
                cg.setPassThreshold(passThreshold);
                cg.setScoringGuide(records.isEmpty() ? null : records.get(0).scoringGuide);

                double avgRaw = records.stream().mapToDouble(r -> r.rawScore).average().orElse(0);
                double avgNorm = records.stream().mapToDouble(r -> r.normScore).average().orElse(0);
                cg.setAvgRawScore(avgRaw);
                cg.setAvgNormScore(avgNorm);

                int cPass = 0, cFail = 0;
                List<RubricCountResult.CriteriaDataPoint> dataPoints = new ArrayList<>();

                for (CriteriaRawRecord rec : records) {
                    boolean passed = rec.normScore >= passThreshold;
                    if (passed) cPass++;
                    else cFail++;

                    RubricCountResult.CriteriaDataPoint dp = new RubricCountResult.CriteriaDataPoint();
                    dp.setDataIndex(rec.dataIndex);
                    dp.setRawScore(rec.rawScore);
                    dp.setNormScore(rec.normScore);
                    dp.setReason(rec.reason);
                    dp.setPassed(passed);
                    dataPoints.add(dp);
                }

                cg.setPassCount(cPass);
                cg.setFailCount(cFail);
                cg.setPassRate(records.size() > 0 ? (double) cPass / records.size() : 0);
                cg.setFailRate(records.size() > 0 ? (double) cFail / records.size() : 0);
                cg.setDataPoints(dataPoints);

                criteriaGroups.add(cg);
            }

            metricGroup.setCriteriaGroups(criteriaGroups);
            metricGroups.add(metricGroup);
        }

        result.setMetricGroups(metricGroups);
        return result;
    }

    // ==================== 内部传递对象 ====================

    /**
     * 单个样本在某个维度上的原始记录（聚合前临时使用）。
     */
    private static class CriteriaRawRecord {
        final Long dataIndex;
        final double rawScore;
        final double normScore;
        final double passThreshold;
        final String reason;
        final String scoringGuide;

        CriteriaRawRecord(Long dataIndex, double rawScore, double normScore, double passThreshold, String reason, String scoringGuide) {
            this.dataIndex = dataIndex;
            this.rawScore = rawScore;
            this.normScore = normScore;
            this.passThreshold = passThreshold;
            this.reason = reason;
            this.scoringGuide = scoringGuide;
        }
    }
}
