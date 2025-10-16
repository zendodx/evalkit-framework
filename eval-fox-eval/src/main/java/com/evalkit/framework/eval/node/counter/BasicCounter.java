package com.evalkit.framework.eval.node.counter;

import com.evalkit.framework.eval.model.*;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

import static com.evalkit.framework.common.utils.statics.StaticsUtils.*;

/**
 * 基础统计器
 */
public class BasicCounter extends Counter {

    /**
     * 统计方法实现
     */
    @Override
    protected CountResult count(List<DataItem> dataItems) {
        BasicCountResult result = new BasicCountResult();
        if (CollectionUtils.isEmpty(dataItems)) {
            return result;
        }
        calRate(dataItems, result);
        calApiCompletionTimeCost(dataItems, result);
        calEvalTimeCost(dataItems, result);
        calEvalScore(dataItems, result);
        return result;
    }

    /**
     * 计算通过率/不通过率/评测错误率/接口调用成功率/接口调用错误率指标
     */
    public void calRate(List<DataItem> dataItems, BasicCountResult result) {
        if (CollectionUtils.isEmpty(dataItems)) {
            return;
        }
        double passRate;
        double unPassRate;
        double evalErrorRate;
        double evalSuccessRate;
        double completionErrorRate;
        double completionSuccessRate;
        int totalCount = dataItems.size();
        int passCount = 0;
        int unPassCount = 0;
        int evalErrorCount = 0;
        int evalSuccessCount = 0;
        int completionErrorCount = 0;
        int completionSuccessCount = 0;
        for (DataItem dataItem : dataItems) {
            EvalResult evalResult = dataItem.getEvalResult();
            if (evalResult == null) {
                continue;
            }
            if (evalResult.isPass()) {
                passCount++;
            } else {
                unPassCount++;
            }
            ApiCompletionResult completionResult = dataItem.getApiCompletionResult();
            if (completionResult.isSuccess()) {
                completionSuccessCount++;
            } else {
                completionErrorCount++;
            }
            if (evalResult.isSuccess()) {
                evalSuccessCount++;
            } else {
                evalErrorCount++;
            }
        }
        passRate = (double) passCount / totalCount;
        unPassRate = (double) unPassCount / totalCount;
        evalErrorRate = (double) evalErrorCount / totalCount;
        evalSuccessRate = (double) evalSuccessCount / totalCount;
        completionErrorRate = (double) completionErrorCount / totalCount;
        completionSuccessRate = (double) completionSuccessCount / totalCount;
        result.setPassRate(passRate);
        result.setUnPassRate(unPassRate);
        result.setEvalErrorRate(evalErrorRate);
        result.setEvalSuccessRate(evalSuccessRate);
        result.setCompletionErrorRate(completionErrorRate);
        result.setCompletionSuccessRate(completionSuccessRate);
        result.setTotalCount(totalCount);
        result.setPassCount(passCount);
        result.setUnPassCount(unPassCount);
        result.setEvalErrorCount(evalErrorCount);
        result.setEvalSuccessCount(evalSuccessCount);
        result.setCompletionErrorCount(completionErrorCount);
        result.setCompletionSuccessCount(completionSuccessCount);
    }

    /**
     * 计算接口调用耗时指标
     */
    public void calApiCompletionTimeCost(List<DataItem> dataItems, BasicCountResult result) {
        List<Long> timeCosts = dataItems.stream()
                .filter(dataItem -> dataItem.getApiCompletionResult() != null)
                .filter(dataItem -> dataItem.getApiCompletionResult().isSuccess())
                .map(dataItem -> dataItem.getApiCompletionResult().getTimeCost())
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(timeCosts)) {
            return;
        }
        result.setCompletionAvgTimeCost(avg(timeCosts));
        result.setCompletionMinTimeCost(min(timeCosts));
        result.setCompletionMaxTimeCost(max(timeCosts));
        result.setCompletionTP99TimeCost(tp(timeCosts, 99));
        result.setCompletionTP95TimeCost(tp(timeCosts, 95));
        result.setCompletionTP90TimeCost(tp(timeCosts, 90));
        result.setCompletionTP80TimeCost(tp(timeCosts, 80));
        result.setCompletionTP70TimeCost(tp(timeCosts, 70));
        result.setCompletionTP60TimeCost(tp(timeCosts, 60));
        result.setCompletionTP50TimeCost(tp(timeCosts, 50));
    }

    /**
     * 计算评测耗时指标
     */
    public void calEvalTimeCost(List<DataItem> dataItems, BasicCountResult result) {
        List<Long> timeCosts = dataItems.stream()
                .filter(dataItem -> dataItem.getEvalResult() != null)
                .filter(dataItem -> dataItem.getEvalResult().isSuccess())
                .map(dataItem -> dataItem.getEvalResult().getTimeCost())
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(timeCosts)) {
            return;
        }
        result.setEvalAvgTimeCost(avg(timeCosts));
        result.setEvalMinTimeCost(min(timeCosts));
        result.setEvalMaxTimeCost(max(timeCosts));
    }

    /**
     * 计算评测分数相关指标
     */
    public void calEvalScore(List<DataItem> dataItems, BasicCountResult result) {
        List<Double> scores = dataItems.stream()
                .filter(dataItem -> dataItem.getEvalResult() != null)
                .filter(dataItem -> dataItem.getEvalResult().isSuccess())
                .map(dataItem -> dataItem.getEvalResult().getScore())
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(scores)) {
            return;
        }
        result.setMinScore(min(scores));
        result.setMaxScore(max(scores));
        result.setAvgScore(avg(scores));
        result.setTp99Score(tp(scores, 99));
        result.setTp95Score(tp(scores, 95));
        result.setTp90Score(tp(scores, 90));
        result.setTp80Score(tp(scores, 80));
        result.setTp70Score(tp(scores, 70));
        result.setTp60Score(tp(scores, 60));
        result.setTp50Score(tp(scores, 50));
        result.setScoreStdDev(standardDeviation(scores));
    }
}
