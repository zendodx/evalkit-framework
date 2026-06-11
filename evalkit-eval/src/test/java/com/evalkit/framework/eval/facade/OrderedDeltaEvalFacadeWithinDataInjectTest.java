package com.evalkit.framework.eval.facade;

import com.evalkit.framework.common.utils.file.FileUtils;
import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.common.utils.time.DateUtils;
import com.evalkit.framework.eval.facade.config.DeltaEvalConfig;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.begin.Begin;
import com.evalkit.framework.eval.node.begin.config.BeginConfig;
import com.evalkit.framework.eval.node.counter.BasicCounter;
import com.evalkit.framework.eval.node.dataloader.JsonFileDataLoader;
import com.evalkit.framework.eval.node.dataloader.config.JsonFileDataLoaderConfig;
import com.evalkit.framework.eval.node.reporter.CsvReporter;
import com.evalkit.framework.eval.node.reporter.ExcelReporter;
import com.evalkit.framework.eval.node.reporter.JsonReporter;
import com.evalkit.framework.eval.node.reporter.html.HtmlReporter;
import com.evalkit.framework.eval.node.scorer.Scorer;
import com.evalkit.framework.eval.node.scorer.config.ScorerConfig;
import com.evalkit.framework.eval.node.scorer.strategy.SumScoreStrategy;
import com.evalkit.framework.workflow.Workflow;
import com.evalkit.framework.workflow.WorkflowBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@Slf4j
class OrderedDeltaEvalFacadeWithinDataInjectTest {
    /**
     * 自定义增量评测
     */
    static class CustomDeltaEval extends OrderedDeltaEvalFacade {
        public CustomDeltaEval(DeltaEvalConfig config) {
            super(config);
        }

        @Override
        public String prepareOrderKey(InputData inputData) {
            Integer caseId = inputData.get("caseId", null);
            if (caseId == null) {
                throw new IllegalArgumentException("Prepare ordered key error because caseId is null");
            }
            return caseId.toString();
        }

        @Override
        public Comparator<InputData> prepareComparator() {
            return (o1, o2) -> {
                try {
                    Integer r1 = o1.get("round", null);
                    Integer r2 = o2.get("round", null);
                    if (r1 == null || r2 == null) {
                        throw new IllegalArgumentException("Prepare comparator error because round is null");
                    }
                    return r1 - r2;
                } catch (Exception ignored) {

                }
                return 0;
            };
        }

        @Override
        protected void afterLoadData() {
            log.info("===>Finish load data, data size:{}", getRemainDataCount());
        }

        @Override
        protected void afterExecute() {
            log.info("===>Finish consume and eval, remain data size:{}, processed data size:{}", getRemainDataCount(), getProcessedDataCount());
            List<File> files = FileUtils.listFiles(config.getAttachDir());
            List<String> collect = files.stream().map(File::getName).collect(Collectors.toList());
            log.info("===>attaches files:{}", collect);
        }
    }

    private File tempJsonFile;

    @BeforeEach
    void setUp() throws IOException {
        // 运行时动态创建临时 JSON 测试文件，不依赖外部文件路径或 secret.properties
        // 构造符合 openInjectData 模式的嵌套数据格式（$.dataItems 数组）：
        //   item.dataIndex      → DataInjector.injectDataIndex 读取（Long 类型）
        //   item.inputData      → DataInjector.injectInputData 读取，包含业务字段
        //     inputData.dataIndex
        //     inputData.inputItem → 实际业务字段（caseId、round、query）
        // 构建 3 个 caseId，每个 caseId 有 2 轮数据，共 6 条
        List<Map<String, Object>> dataItems = new ArrayList<>();
        long idx = 0L;
        for (int caseId = 1; caseId <= 3; caseId++) {
            for (int round = 1; round <= 2; round++) {
                // 业务字段放在 inputItem 中
                Map<String, Object> inputItem = new HashMap<>();
                inputItem.put("caseId", caseId);
                inputItem.put("round", round);
                inputItem.put("query", "caseId=" + caseId + " round=" + round + " 测试问题");

                // 嵌套的 inputData 对象
                Map<String, Object> inputData = new HashMap<>();
                inputData.put("dataIndex", idx);
                inputData.put("inputItem", inputItem);

                // 顶层 item
                Map<String, Object> item = new HashMap<>();
                item.put("dataIndex", idx);
                item.put("inputData", inputData);
                dataItems.add(item);
                idx++;
            }
        }
        Map<String, Object> jsonContent = new HashMap<>();
        jsonContent.put("dataItems", dataItems);

        // 写入临时文件
        tempJsonFile = File.createTempFile("ordered_delta_eval_inject_test_", ".json");
        tempJsonFile.deleteOnExit();
        Files.write(tempJsonFile.toPath(), JsonUtils.toJson(jsonContent).getBytes(StandardCharsets.UTF_8));
        log.info("Created temp test file: {}", tempJsonFile.getAbsolutePath());
    }

    @AfterEach
    void tearDown() {
        if (tempJsonFile != null && tempJsonFile.exists()) {
            tempJsonFile.delete();
        }
    }

    @Test
    public void test() throws Exception {
        // 使用运行时创建的临时文件，不依赖外部文件或 secret.properties
        String filePath = tempJsonFile.getAbsolutePath();
        JsonFileDataLoader jsonFileDataLoader = new JsonFileDataLoader(
                JsonFileDataLoaderConfig.builder()
                        .jsonPath("$.dataItems")
                        .filePath(filePath)
                        .openInjectData(true)
                        .build()
        );

        // 评测工作流
        Begin begin = new Begin(
                BeginConfig.builder()
                        .threshold(1)
                        .scoreStrategy(new SumScoreStrategy())
                        .build()
        );
        Scorer scorer1 = new Scorer() {
            @Override
            public ScorerResult eval(DataItem dataItem) {
                ScorerResult scorerResult = new ScorerResult();
                scorerResult.setMetric("eval-test-1");
                scorerResult.setScore(1.0);
                scorerResult.setReason("eval test1:" + dataItem.getInputData().get("query"));
                return scorerResult;
            }
        };
        Scorer scorer2 = new Scorer(
                ScorerConfig.builder()
                        .star(true)
                        .threshold(1)
                        .metricName("eval-test-2")
                        .build()
        ) {
            @Override
            public ScorerResult eval(DataItem dataItem) {
                ScorerResult scorerResult = new ScorerResult();
                scorerResult.setMetric("eval-test-2");
                scorerResult.setScore(1.0);
                scorerResult.setReason("eval test2:" + dataItem.getInputData().get("query"));
                return scorerResult;
            }
        };
        Scorer scorer3 = new Scorer() {
            @Override
            public ScorerResult eval(DataItem dataItem) {
                ScorerResult scorerResult = new ScorerResult();
                scorerResult.setMetric("eval-test-3");
                scorerResult.setScore(0);
                scorerResult.setReason("eval test3:" + dataItem.getInputData().get("query"));
                return scorerResult;
            }
        };

        // 评测结果上报
        String taskName = "OrderedDeltaEvalWithinDataInjectTest";
        String attachDir = "attachments/" + taskName;
        String fileName = "ordered_delta_eval_within_datainject_test_" + DateUtils.nowToString();
        BasicCounter basicCounter = new BasicCounter();
        HtmlReporter htmlReporter = new HtmlReporter(fileName, attachDir);
        JsonReporter jsonReporter = new JsonReporter(fileName, attachDir);
        ExcelReporter excelReporter = new ExcelReporter(fileName, attachDir);
        CsvReporter csvReporter = new CsvReporter(fileName, attachDir);

        List<Scorer> scorers = ListUtils.of(scorer1, scorer2, scorer3);

        Workflow evalWorkflow = new WorkflowBuilder()
                .link(begin, scorers).build();
        Workflow reportWorkflow = new WorkflowBuilder()
                .link(basicCounter, htmlReporter, jsonReporter, excelReporter, csvReporter).build();

        CustomDeltaEval cfe = new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(jsonFileDataLoader)
                        .evalWorkflow(evalWorkflow)
                        .reportWorkflow(reportWorkflow)
                        .batchSize(10)
                        .threadNum(10)
                        .enableResume(false)
                        .build()
        );

        // 必须在指定时间内跑完，否则认为死锁 / 阻塞
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(180), (ThrowingSupplier<Void>) () -> {
            cfe.run();
            return null;
        });
    }
}