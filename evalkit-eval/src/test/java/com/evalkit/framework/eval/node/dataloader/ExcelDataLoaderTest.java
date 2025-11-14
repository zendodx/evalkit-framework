package com.evalkit.framework.eval.node.dataloader;

import com.evalkit.framework.eval.node.dataloader.config.ExcelDataLoaderConfig;
import org.junit.jupiter.api.Test;

class ExcelDataLoaderTest {
    @Test
    void validConfigTest() {
        ExcelDataLoader excelDataLoader = new ExcelDataLoader(
                ExcelDataLoaderConfig.builder().filePath("test.xlsx").build()
        );
    }
}