package com.evalkit.framework.eval.node.dataloader.datagenerator;

import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.eval.node.dataloader.datagen.DataGenerator;
import com.evalkit.framework.eval.node.dataloader.datagen.config.DataGeneratorConfig;
import com.evalkit.framework.eval.node.dataloader.datagen.exporter.ExcelGenDataExporter;
import com.evalkit.framework.eval.node.dataloader.datagen.exporter.JsonFileGenDataExporter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class DataGeneratorTest {
    @Test
    void test() throws Exception {
        DataGenerator dataGenerator = new DataGenerator(
                DataGeneratorConfig.builder()
                        .enableOutputFile(true)
                        .genDataExporterList(
                                ListUtils.of(new ExcelGenDataExporter(), new JsonFileGenDataExporter())
                        )
                        .build()
        ) {
            @Override
            public List<Map<String, Object>> generate() {
                return ListUtils.of(
                        MapUtils.of("query", "hello", "type", 1),
                        MapUtils.of("query", "hi", "type", 2)
                );
            }
        };
        dataGenerator.prepareDataList();
    }
}