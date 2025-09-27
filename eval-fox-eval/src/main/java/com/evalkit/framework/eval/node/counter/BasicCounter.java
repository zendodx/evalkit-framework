package com.evalkit.framework.eval.node.counter;

import com.evalkit.framework.eval.model.*;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
                .filter(dataItem -> dataItem.getApiCompletionResult().isSuccess())
                .map(dataItem -> dataItem.getApiCompletionResult().getTimeCost())
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(timeCosts)) {
            return;
        }
        long completionAvgTimeCost;
        long completionMinTimeCost;
        long completionMaxTimeCost;
        long completionTP99TimeCost;
        long completionTP95TimeCost;
        long completionTP90TimeCost;
        long completionTP80TimeCost;
        long completionTP70TimeCost;
        long completionTP60TimeCost;
        long completionTP50TimeCost;
        completionAvgTimeCost = timeCosts.stream().mapToLong(Long::longValue).sum() / timeCosts.size();
        completionMinTimeCost = timeCosts.stream().min(Long::compareTo).get();
        completionMaxTimeCost = timeCosts.stream().max(Long::compareTo).get();
        completionTP99TimeCost = calTP(timeCosts, 99);
        completionTP95TimeCost = calTP(timeCosts, 95);
        completionTP90TimeCost = calTP(timeCosts, 90);
        completionTP80TimeCost = calTP(timeCosts, 80);
        completionTP70TimeCost = calTP(timeCosts, 70);
        completionTP60TimeCost = calTP(timeCosts, 60);
        completionTP50TimeCost = calTP(timeCosts, 50);

        result.setCompletionAvgTimeCost(completionAvgTimeCost);
        result.setCompletionMinTimeCost(completionMinTimeCost);
        result.setCompletionMaxTimeCost(completionMaxTimeCost);
        result.setCompletionTP99TimeCost(completionTP99TimeCost);
        result.setCompletionTP95TimeCost(completionTP95TimeCost);
        result.setCompletionTP90TimeCost(completionTP90TimeCost);
        result.setCompletionTP80TimeCost(completionTP80TimeCost);
        result.setCompletionTP70TimeCost(completionTP70TimeCost);
        result.setCompletionTP60TimeCost(completionTP60TimeCost);
        result.setCompletionTP50TimeCost(completionTP50TimeCost);
    }

    /**
     * 计算评测耗时指标
     */
    public void calEvalTimeCost(List<DataItem> dataItems, BasicCountResult result) {
        List<Long> timeCosts = dataItems.stream()
                .filter(dataItem -> dataItem.getEvalResult().isSuccess())
                .map(dataItem -> dataItem.getEvalResult().getTimeCost())
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(timeCosts)) {
            return;
        }
        long evalAvgTimeCost;
        long evalMinTimeCost;
        long evalMaxTimeCost;
        evalAvgTimeCost = timeCosts.stream().mapToLong(Long::longValue).sum() / timeCosts.size();
        evalMinTimeCost = timeCosts.stream().min(Long::compareTo).get();
        evalMaxTimeCost = timeCosts.stream().max(Long::compareTo).get();
        result.setEvalAvgTimeCost(evalAvgTimeCost);
        result.setEvalMinTimeCost(evalMinTimeCost);
        result.setEvalMaxTimeCost(evalMaxTimeCost);
    }

    /**
     * 计算TP
     */
    private long calTP(List<Long> timeCosts, int TP) {
        if (timeCosts == null || timeCosts.isEmpty()) {
            throw new IllegalArgumentException("timeCosts must not be empty");
        }
        if (TP <= 0 || TP > 100) {
            throw new IllegalArgumentException("TP must be in (0,100]");
        }
        List<Long> sorted = new ArrayList<>(timeCosts);
        Collections.sort(sorted);
        int index = (int) Math.ceil(sorted.size() * TP / 100.0);
        if (index == 0) index = 1;
        return sorted.get(index - 1);
    }
}
