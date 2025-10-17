package com.evalkit.framework.eval.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 上报数据
 */
@Data
public class ReportData {
    /* 评测项结果 */
    private List<DataItem> dataItems;
    /* 评测统计结果 */
    private Map<String, String> countResultMap;

    public ReportData(List<DataItem> dataItems, Map<String, String> countResultMap) {
        this.dataItems = dataItems;
        this.countResultMap = countResultMap;
    }
}
