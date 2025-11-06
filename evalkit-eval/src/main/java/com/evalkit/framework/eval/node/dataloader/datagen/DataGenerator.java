package com.evalkit.framework.eval.node.dataloader.datagen;

import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.eval.node.dataloader.datagen.config.DataGeneratorConfig;
import com.evalkit.framework.eval.node.dataloader.datagen.exporter.GenDataExporter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据生成器基类
 */
@Slf4j
public abstract class DataGenerator extends DataLoader {
    protected final DataGeneratorConfig config;

    public DataGenerator() {
        config = DataGeneratorConfig.builder().build();
    }

    protected DataGenerator(DataGeneratorConfig config) {
        this.config = config;
    }

    @Override
    public List<InputData> prepareDataList() throws Exception {
        List<InputData> inputDataList = new ArrayList<>();
        List<Map<String, Object>> dataList = generate();
        for (Map<String, Object> data : dataList) {
            // 过滤掉空数据
            if (data == null) {
                continue;
            }
            // 如果字段值是null会报空指针错误,设置为""
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                Object value = entry.getValue();
                if (value == null) {
                    entry.setValue("");
                }
            }
            InputData inputData = new InputData(data);
            inputDataList.add(inputData);
        }
        // 开启文件导出
        if (config.isEnableOutputFile() && CollectionUtils.isNotEmpty(config.getGenDataExporterList())) {
            // 如果没有导出文件夹则先创建
            String outputFilePath = config.getOutputFilePath();
            String outputFileName = config.getOutputFileName();
            Path gendata = Paths.get(outputFilePath + "/");
            if (!gendata.toFile().exists()) {
                gendata.toFile().mkdirs();
            }
            List<GenDataExporter> genDataExporterList = config.getGenDataExporterList();
            for (GenDataExporter genDataExporter : genDataExporterList) {
                String exportFile = genDataExporter.export(inputDataList, outputFilePath, outputFileName);
                log.info("Export gen data success, export file: {} ", exportFile);
            }
        }
        return inputDataList;
    }

    /**
     * 生成数据
     *
     * @return 返回Map集合
     */
    public abstract List<Map<String, Object>> generate();
}