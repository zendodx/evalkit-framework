package com.evalkit.framework.eval.core;

import com.evalkit.framework.common.utils.file.FileUtils;
import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.common.utils.runtime.RuntimeEnvUtils;
import com.evalkit.framework.common.utils.time.DateUtils;
import com.evalkit.framework.eval.model.*;
import com.evalkit.framework.eval.node.api.ApiCompletion;
import com.evalkit.framework.eval.node.begin.Begin;
import com.evalkit.framework.eval.node.begin.config.BeginConfig;
import com.evalkit.framework.eval.node.counter.AttributeCounter;
import com.evalkit.framework.eval.node.counter.BasicCounter;
import com.evalkit.framework.eval.node.counter.Counter;
import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.eval.node.dataloader.config.DataLoaderConfig;
import com.evalkit.framework.eval.node.dataloader_wrapper.DataLoaderWrapper;
import com.evalkit.framework.eval.node.dataloader_wrapper.MockDataLoaderWrapper;
import com.evalkit.framework.eval.node.end.End;
import com.evalkit.framework.eval.node.reporter.CsvReporter;
import com.evalkit.framework.eval.node.reporter.ExcelReporter;
import com.evalkit.framework.eval.node.reporter.JsonReporter;
import com.evalkit.framework.eval.node.reporter.Reporter;
import com.evalkit.framework.eval.node.reporter.html.HtmlReporter;
import com.evalkit.framework.eval.node.scorer.Scorer;
import com.evalkit.framework.eval.node.scorer.VectorSimilarityScorer;
import com.evalkit.framework.eval.node.scorer.config.ScorerConfig;
import com.evalkit.framework.eval.node.scorer.strategy.AvgScoreRateStrategy;
import com.evalkit.framework.eval.node.scorer.strategy.SumScoreStrategy;
import com.evalkit.framework.infra.service.llm.LLMService;
import com.evalkit.framework.infra.service.llm.LLMServiceFactory;
import com.evalkit.framework.infra.service.llm.config.DeepseekLLMServiceConfig;
import com.evalkit.framework.infra.service.llm.constants.LLMServiceEnum;
import com.evalkit.framework.workflow.WorkflowBuilder;
import com.evalkit.framework.workflow.model.WorkflowContext;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 节点编排核心链路测试
 */
@Slf4j
public class CoreTest {
    Begin begin;
    DataLoader dataLoader;
    DataLoaderWrapper dataLoaderWrapper;
    ApiCompletion apiCompletion;
    Scorer scorer1;
    Scorer scorer2;
    BasicCounter basicCounter;
    AttributeCounter attributeCounter;
    Reporter reporter;
    HtmlReporter htmlReporter;
    CsvReporter csvReporter;
    ExcelReporter excelReporter;
    JsonReporter jsonReporter;
    End end;

    @BeforeEach
    public void init() {
        String deepSeekToken = RuntimeEnvUtils.getPropertyFromResource("secret.properties", "deepseek-token");
        DeepseekLLMServiceConfig config = DeepseekLLMServiceConfig.builder().apiToken(deepSeekToken).build();
        LLMService llmService = LLMServiceFactory.createLLMService(LLMServiceEnum.DEEPSEEK.name(), config);

        begin = new Begin(
                BeginConfig.builder()
                        .scoreStrategy(new AvgScoreRateStrategy())
                        .threshold(1)
                        .build()
        );

        dataLoader = new DataLoader(DataLoaderConfig.builder().shuffle(true).build()) {
            @Override
            public List<InputData> prepareDataList() {
                List<InputData> inputDatas = new ArrayList<>();
                inputDatas.add(new InputData(1L, JsonUtils.fromJson("{\t\"query\":\"hello, {{holiday}}\",\"type\":\"1\"}", new TypeReference<Map<String, Object>>() {
                })));
                return inputDatas;
            }
        };

        dataLoaderWrapper = new MockDataLoaderWrapper() {
            @Override
            public List<String> selectMockFields() {
                List<String> mockFields = new ArrayList<>();
                mockFields.add("query");
                return mockFields;
            }
        };

        apiCompletion = new ApiCompletion() {
            @Override
            public ApiCompletionResult invoke(DataItem dataItem) {
                InputData inputData = dataItem.getInputData();
                String query = inputData.get("query");
                Map<String, Object> r = new HashMap<>();
                r.put("response", "Mock response for " + query);
                Map<String, Object> result = new HashMap<>(r);
                return new ApiCompletionResult(result);
            }
        };

        scorer1 = new Scorer(
                ScorerConfig.builder()
                        .metricName("回复长度检查")
                        .totalScore(1)
                        .build()) {
            @Override
            public ScorerResult eval(DataItem dataItem) {
                InputData inputData = dataItem.getInputData();
                ApiCompletionResult apiCompletionResult = dataItem.getApiCompletionResult();
                String query = inputData.get("query");
                String response = apiCompletionResult.get("response");
                ScorerResult result = new ScorerResult();
                result.setDataIndex(inputData.getDataIndex());
                result.setMetric("回复长度检查");
                if (response.length() > 10) {
                    result.setScore(1.0);
                    result.setReason(query + " 的回复长度超过5个字符");
                } else {
                    result.setScore(0.0);
                    result.setReason(query + " 的回复长度不超过5个字符");
                }
                return result;
            }
        };

        scorer2 = new VectorSimilarityScorer(
                ScorerConfig.builder()
                        .metricName("相似度检查level1")
                        .threshold(0)
                        .totalScore(1)
                        .build(),
                0) {
            @Override
            public Pair<String, String> prepareFieldPair(DataItem dataItem) {
                return new ImmutablePair<>("query", "response");
            }
        };

        basicCounter = new BasicCounter();
        attributeCounter = new AttributeCounter(llmService);

        reporter = new Reporter() {
            @Override
            public void report(ReportData reportData) {
                List<DataItem> dataItems = reportData.getDataItems();
                Map<String, String> countResultMap = reportData.getCountResultMap();
                log.info("data items: {}", JsonUtils.toJson(dataItems));
                log.info("count results: {}", countResultMap);
            }
        };

        String fileName = "核心链路测试_" + DateUtils.nowToString();
        String parentDir = "attaches/核心链路测试";
        htmlReporter = new HtmlReporter(fileName, parentDir);
        csvReporter = new CsvReporter(fileName, parentDir);
        excelReporter = new ExcelReporter(fileName, parentDir);
        jsonReporter = new JsonReporter(fileName, parentDir);

        end = new End() {
            @Override
            public void process(WorkflowContext ctx) {
                List<File> attaches = FileUtils.listFiles(parentDir);
                List<String> fileNames = attaches.stream().map(File::getName).collect(Collectors.toList());
                log.info("attaches: {}", fileNames);
            }
        };
    }

    @Test
    public void test() {
        List<Scorer> scorers = ListUtils.of(scorer1, scorer2);
        List<Counter> counters = ListUtils.of(basicCounter, attributeCounter);
        List<Reporter> reporters = ListUtils.of(reporter, htmlReporter, csvReporter, excelReporter, jsonReporter);
        new WorkflowBuilder()
                .link(begin, dataLoader, dataLoaderWrapper, apiCompletion)
                .link(apiCompletion, scorers)
                .link(scorers, counters, reporters)
                .link(reporters, end)
                .build()
                .execute();
    }
}
