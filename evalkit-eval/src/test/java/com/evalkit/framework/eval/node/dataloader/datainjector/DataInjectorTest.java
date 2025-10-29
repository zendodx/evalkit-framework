package com.evalkit.framework.eval.node.dataloader.datainjector;

import com.evalkit.framework.common.utils.runtime.RuntimeEnvUtils;
import com.evalkit.framework.common.utils.time.DateUtils;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.begin.Begin;
import com.evalkit.framework.eval.node.counter.BasicCounter;
import com.evalkit.framework.eval.node.dataloader.JsonDataLoader;
import com.evalkit.framework.eval.node.dataloader.JsonFileDataLoader;
import com.evalkit.framework.eval.node.dataloader.config.JsonFileDataLoaderConfig;
import com.evalkit.framework.eval.node.reporter.CsvReporter;
import com.evalkit.framework.eval.node.reporter.ExcelReporter;
import com.evalkit.framework.eval.node.reporter.JsonReporter;
import com.evalkit.framework.eval.node.reporter.html.HtmlReporter;
import com.evalkit.framework.eval.node.scorer.Scorer;
import com.evalkit.framework.workflow.WorkflowBuilder;
import org.junit.jupiter.api.Test;

class DataInjectorTest {
    String filePath = RuntimeEnvUtils.getPropertyFromResource("secret.properties", "json-file-datainjector-test-file");

    @Test
    void test() {
        Begin begin = new Begin();

        JsonDataLoader jsonDataLoader = new JsonFileDataLoader(
                JsonFileDataLoaderConfig.builder()
                        .jsonPath("$.dataItems")
                        .filePath(filePath)
                        .openInjectData(true)
                        .build()
        );

        BasicCounter basicCounter = new BasicCounter();

        Scorer scorer99 = new Scorer() {
            @Override
            public ScorerResult eval(DataItem dataItem) throws Exception {
                return new ScorerResult("评估器99", 0, 1, "无理由", null);
            }
        };

        String fileName = "DataInjectorTest_" + DateUtils.nowToString("yyyyMMddHHmmss");
        HtmlReporter htmlReporter = new HtmlReporter(fileName, fileName);
        JsonReporter jsonReporter = new JsonReporter(fileName, fileName);
        ExcelReporter excelReporter = new ExcelReporter(fileName, fileName);
        CsvReporter csvReporter = new CsvReporter(fileName, fileName);

        new WorkflowBuilder().link(begin, jsonDataLoader, scorer99, basicCounter, htmlReporter, jsonReporter, excelReporter, csvReporter).build().execute();
    }
}