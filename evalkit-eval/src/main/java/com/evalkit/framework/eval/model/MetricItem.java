package com.evalkit.framework.eval.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetricItem {
    /* 指标对应的数据索引 */
    private Long dataIndex;
    /* 指标名称 */
    private String metricName;
    /* 指标值 */
    private Double metricValue;
    /* 指标阈值 */
    private Double metricThreshold;
}
