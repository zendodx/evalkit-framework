package com.evalkit.framework.eval.node.reporter;


import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.ReportData;
import com.evalkit.framework.common.utils.json.JsonUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 打印结果
 */
public class StdReporter extends Reporter {
    @Override
    protected void report(ReportData reportData) throws IOException {
        List<DataItem> dataItems = reportData.getDataItems();
        Map<String, String> countResultMap = reportData.getCountResultMap();
        System.out.println("\n------------评测Case------------\n");
        dataItems.forEach(dataItem -> {
            System.out.println(JsonUtils.toJson(dataItem));
            System.out.println();
        });
        System.out.println("\n------------评测统计------------\n");
        countResultMap.forEach((key, value) -> {
            System.out.printf("\n------------%s------------\n%n", key);
            System.out.println(JsonUtils.toJson(value));
        });
        System.out.println("\n--------------------------------\n");
    }
}
