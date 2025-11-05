package com.evalkit.framework.eval.node.dataloader.datagenerator.exporter;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.eval.model.InputData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JSON文件生成数据导出器
 */
public class JsonFileGenDataExporter implements GenDataExporter {

    @Override
    public String export(List<InputData> inputDataList, String outputPath, String fileName) {
        String suffix = ".json";
        String filePath = outputPath + "/" + fileName + suffix;
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (InputData inputData : inputDataList) {
            dataList.add(inputData.getInputItem());
        }
        JsonUtils.writeJsonFile(filePath, dataList);
        return filePath;
    }
}
