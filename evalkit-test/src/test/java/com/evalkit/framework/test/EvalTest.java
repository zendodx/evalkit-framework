package com.evalkit.framework.test;

import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.common.utils.time.DateUtils;
import com.evalkit.framework.eval.facade.DeltaEvalFacade;
import com.evalkit.framework.eval.facade.FullEvalFacade;
import com.evalkit.framework.eval.facade.config.DeltaEvalConfig;
import com.evalkit.framework.eval.facade.config.FullEvalConfig;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.api.ApiCompletion;
import com.evalkit.framework.eval.node.begin.Begin;
import com.evalkit.framework.eval.node.begin.config.BeginConfig;
import com.evalkit.framework.eval.node.counter.BasicCounter;
import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.eval.node.dataloader.ExcelDataLoader;
import com.evalkit.framework.eval.node.dataloader_wrapper.DataLoaderWrapper;
import com.evalkit.framework.eval.node.dataloader_wrapper.MockDataLoaderWrapper;
import com.evalkit.framework.eval.node.reporter.CsvReporter;
import com.evalkit.framework.eval.node.reporter.ExcelReporter;
import com.evalkit.framework.eval.node.reporter.JsonReporter;
import com.evalkit.framework.eval.node.reporter.Reporter;
import com.evalkit.framework.eval.node.reporter.html.HtmlReporter;
import com.evalkit.framework.eval.node.scorer.Scorer;
import com.evalkit.framework.eval.node.scorer.VectorSimilarityScorer;
import com.evalkit.framework.eval.node.scorer.config.ScorerConfig;
import com.evalkit.framework.eval.node.scorer.config.VectorSimilarityScorerConfig;
import com.evalkit.framework.eval.node.scorer.strategy.MaxScoreRateStrategy;
import com.evalkit.framework.workflow.Workflow;
import com.evalkit.framework.workflow.WorkflowBuilder;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评测测试工具类
 */
public class EvalTest {
    /* ----- 工作流节点 ----- */
    DataLoader dataLoader;
    DataLoaderWrapper mockDataLoaderWrapper;
    ApiCompletion apiCompletion;
    Scorer scorer1;
    Scorer scorer2;
    BasicCounter basicCounter;
    Begin begin;
    HtmlReporter htmlReporter;
    CsvReporter csvReporter;
    ExcelReporter excelReporter;
    JsonReporter jsonReporter;

    /**
     * 初始化工作流节点
     *
     * @param fileName 评测数据路径
     */
    public void initWorkflowNode(String fileName) {
        begin = new Begin(
                BeginConfig.builder()
                        .scoreStrategy(new MaxScoreRateStrategy())
                        .threshold(1)
                        .build()
        );

        dataLoader = new ExcelDataLoader(fileName);

        mockDataLoaderWrapper = new MockDataLoaderWrapper() {
            @Override
            public List<String> selectMockFields() {
                return ListUtils.of("query");
            }
        };

        apiCompletion = new ApiCompletion() {
            @Override
            public ApiCompletionResult invoke(DataItem dataItem) {
                InputData inputData = dataItem.getInputData();
                String query = inputData.get("query");
                Map<String, Object> r = new HashMap<>();
                r.put("response", "Response of " + query);
                Map<String, Object> result = new HashMap<>(r);
                return new ApiCompletionResult(result);
            }
        };

        scorer1 = new Scorer(
                ScorerConfig.builder()
                        .metricName("检查1")
                        .totalScore(1)
                        .build()
        ) {
            @Override
            public ScorerResult eval(DataItem dataItem) throws Exception {
                return new ScorerResult("检查1", 1, 1, "通过");
            }
        };

        scorer2 = new VectorSimilarityScorer(
                VectorSimilarityScorerConfig.builder()
                        .metricName("检查2")
                        .threshold(0)
                        .totalScore(1)
                        .similarityThreshold(0.5)
                        .build()) {
            @Override
            public Pair<String, String> prepareFieldPair(DataItem dataItem) {
                return new ImmutablePair<>("query", "response");
            }
        };

        basicCounter = new BasicCounter();

        String reportFileName = "DAGEvalPerformanceTest_" + DateUtils.nowToString();
        htmlReporter = new HtmlReporter(reportFileName);
        csvReporter = new CsvReporter(reportFileName);
        excelReporter = new ExcelReporter(reportFileName);
        jsonReporter = new JsonReporter(reportFileName);
    }

    /**
     * 构建DAG评测工作流
     *
     * @param fileName 评测数据路径
     * @return DAG评测工作流
     */
    public Workflow buildDAGEvalWorkflow(String fileName) {
        initWorkflowNode(fileName);
        List<Scorer> scorers = ListUtils.of(scorer1, scorer2);
        List<Reporter> reporters = ListUtils.of(htmlReporter, csvReporter, excelReporter, jsonReporter);
        return new WorkflowBuilder()
                .link(begin, dataLoader, mockDataLoaderWrapper, apiCompletion, scorers, basicCounter, reporters)
                .build();
    }

    /**
     * 构建全量评测
     *
     * @param fileName 评测数据路径
     * @return 全量评测
     */
    public FullEvalFacade buildFullEvalFacade(String fileName) {
        initWorkflowNode(fileName);
        List<Scorer> scorers = ListUtils.of(scorer1, scorer2);
        List<Reporter> reporters = ListUtils.of(htmlReporter, csvReporter, excelReporter, jsonReporter);
        return new FullEvalFacade(
                FullEvalConfig.builder()
                        .taskName("FullEvalPerformanceTest_" + DateUtils.nowToString())
                        .dataLoader(dataLoader)
                        .evalWorkflow(new WorkflowBuilder().link(begin, mockDataLoaderWrapper, apiCompletion, scorers).build())
                        .reportWorkflow(new WorkflowBuilder().link(basicCounter, reporters).build())
                        .build()
        );
    }

    /**
     * 构建增量评测
     *
     * @param fileName 评测数据路径
     * @return 增量评测
     */
    public DeltaEvalFacade buildDeltaEvalFacade(String fileName) {
        initWorkflowNode(fileName);
        List<Scorer> scorers = ListUtils.of(scorer1, scorer2);
        List<Reporter> reporters = ListUtils.of(htmlReporter, csvReporter, excelReporter, jsonReporter);
        return new DeltaEvalFacade(
                DeltaEvalConfig.builder()
                        .taskName("DeltaEvalPerformanceTest_" + DateUtils.nowToString())
                        .dataLoader(dataLoader)
                        .evalWorkflow(new WorkflowBuilder().link(begin, mockDataLoaderWrapper, apiCompletion, scorers).build())
                        .reportWorkflow(new WorkflowBuilder().link(basicCounter, reporters).build())
                        .build()
        );
    }
}
