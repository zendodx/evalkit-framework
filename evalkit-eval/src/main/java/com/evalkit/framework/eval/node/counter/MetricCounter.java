package com.evalkit.framework.eval.node.counter;

import com.evalkit.framework.eval.model.CountResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.MetricCountResult;
import com.evalkit.framework.eval.model.MetricItem;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评估指标统计器
 */
public abstract class MetricCounter extends Counter {

    public MetricCounter() {
        super.counterType = "metricCounter";
    }

    @Override
    protected CountResult count(List<DataItem> dataItems) {
        MetricCountResult result = new MetricCountResult();
        List<MetricItem> allMetricItems = buildMetricItems(dataItems);
        List<MetricCountResult.MetricGroup> metricGroups = new ArrayList<>();
        // 按照指标名称分组
        Map<String, List<MetricItem>> metricItemMap = allMetricItems.stream().collect(Collectors.groupingBy(MetricItem::getMetricName));
        for (Map.Entry<String, List<MetricItem>> entry : metricItemMap.entrySet()) {
            String metricName = entry.getKey();
            List<MetricItem> metricItems = entry.getValue();
            if (CollectionUtils.isEmpty(metricItems)) {
                continue;
            }
            // 计算指标最值,平均值,通过率
            List<Double> metricValues = metricItems.stream().map(MetricItem::getMetricValue).collect(Collectors.toList());
            double min = metricValues.stream().min(Double::compareTo).orElse(0.0);
            double max = metricValues.stream().max(Double::compareTo).orElse(0.0);
            double avg = metricValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            // 筛选出检查项通过的指标
            List<MetricItem> passMetricItems = metricItems.stream().filter(item -> item.getMetricValue() >= item.getMetricThreshold()).collect(Collectors.toList());
            // 筛选出检查项未通过的指标
            List<MetricItem> failMetricItems = metricItems.stream().filter(item -> item.getMetricValue() < item.getMetricThreshold()).collect(Collectors.toList());
            int allCount = metricItems.size();
            double passRate = allCount > 0 ? (double) passMetricItems.size() / allCount : 0.0;
            double failRate = allCount > 0 ? (double) failMetricItems.size() / allCount : 0.0;
            MetricCountResult.MetricGroup metricGroup = new MetricCountResult.MetricGroup();
            metricGroup.setMetricName(metricName);
            metricGroup.setMinValue(min);
            metricGroup.setMaxValue(max);
            metricGroup.setAvgValue(avg);
            metricGroup.setPassMetricItems(passMetricItems);
            metricGroup.setFailMetricItems(failMetricItems);
            metricGroup.setFailCount((long) failMetricItems.size());
            metricGroup.setPassCount((long) passMetricItems.size());
            metricGroup.setPassRate(passRate);
            metricGroup.setFailRate(failRate);
            metricGroups.add(metricGroup);
        }
        result.setMetricGroups(metricGroups);
        return result;
    }

    public abstract List<MetricItem> buildMetricItems(List<DataItem> dataItems);
}
