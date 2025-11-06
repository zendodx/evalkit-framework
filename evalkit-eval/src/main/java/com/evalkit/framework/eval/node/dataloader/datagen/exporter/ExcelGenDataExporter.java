package com.evalkit.framework.eval.node.dataloader.datagen.exporter;

import com.evalkit.framework.common.utils.file.ExcelUtils;
import com.evalkit.framework.eval.model.InputData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Excel生成数据导出器
 */
public class ExcelGenDataExporter implements GenDataExporter {

    @Override
    public String export(List<InputData> inputDataList, String outputPath, String fileName) {
        String suffix = ".xlsx";
        String filePath = outputPath + "/" + fileName + suffix;
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (InputData inputData : inputDataList) {
            dataList.add(inputData.getInputItem());
        }
        ExcelUtils.writeExcel(filePath, dataList, true);
        return filePath;
    }
}
