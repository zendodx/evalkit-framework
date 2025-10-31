package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.common.utils.time.DateUtils;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.begin.Begin;
import com.evalkit.framework.eval.node.begin.config.BeginConfig;
import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.eval.node.reporter.html.HtmlReporter;
import com.evalkit.framework.eval.node.scorer.checker.AbstractChecker;
import com.evalkit.framework.eval.node.scorer.checker.Checker;
import com.evalkit.framework.eval.node.scorer.checker.config.CheckerConfig;
import com.evalkit.framework.eval.node.scorer.checker.constants.CheckMethod;
import com.evalkit.framework.eval.node.scorer.checker.model.CheckItem;
import com.evalkit.framework.eval.node.scorer.checker.strategy.checker.MergeCheckerScoreStrategy;
import com.evalkit.framework.eval.node.scorer.config.ScorerConfig;
import com.evalkit.framework.eval.node.scorer.strategy.AvgScoreRateStrategy;
import com.evalkit.framework.workflow.WorkflowBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 多检查器评估器测试类
 */
class MultiCheckerBasedScorerTest {

    /**
     * 必过检查器
     */
    class StarChecker extends AbstractChecker {

        public StarChecker() {
        }

        public StarChecker(CheckerConfig config) {
            super(config);
        }

        /* 必过检查项 */
        private final CheckItem starCheckItem = CheckItem.builder()
                .name("starCheckItem")
                .star(true)
                .build();
        /* 一般检查项 */
        private final CheckItem normalCheckItem = CheckItem.builder()
                .name("normalCheckItem")
                .star(false)
                .build();

        @Override
        protected List<CheckItem> prepareCheckItems(DataItem dataItem) {
            return ListUtils.of(
                    starCheckItem, normalCheckItem
            );
        }

        @Override
        protected void check(DataItem dataItem) {
            // 模拟必过项没过,普通项通过
            starCheckItem.setScore(0);
            starCheckItem.setExecuted(true);
            starCheckItem.setReason("不通过");
            starCheckItem.setCheckMethod(CheckMethod.RULE);

            normalCheckItem.setScore(1);
            normalCheckItem.setExecuted(true);
            normalCheckItem.setReason("通过");
            normalCheckItem.setCheckMethod(CheckMethod.RULE);
        }

        @Override
        public boolean support(DataItem dataItem) {
            return true;
        }

        @Override
        public double getTotalScore() {
            return 2;
        }
    }

    /**
     * 普通检查器
     */
    class NormalChecker extends AbstractChecker {

        public NormalChecker() {
        }

        public NormalChecker(CheckerConfig config) {
            super(config);
        }

        /* 一般检查项 */
        private final CheckItem normalCheckItem = CheckItem.builder()
                .name("normalCheckItem")
                .star(false)
                .build();

        @Override
        protected List<CheckItem> prepareCheckItems(DataItem dataItem) {
            return ListUtils.of(
                    normalCheckItem
            );
        }

        @Override
        protected void check(DataItem dataItem) {
            normalCheckItem.setScore(1);
            normalCheckItem.setExecuted(true);
            normalCheckItem.setReason("通过");
            normalCheckItem.setCheckMethod(CheckMethod.RULE);
        }

        @Override
        public boolean support(DataItem dataItem) {
            return true;
        }

        @Override
        public double getTotalScore() {
            return 1;
        }
    }

    /**
     * 自定义评估器
     */
    class CustomScorer extends MultiCheckerBasedScorer {

        public CustomScorer(MergeCheckerScoreStrategy strategy) {
            super(strategy);
        }

        public CustomScorer(ScorerConfig config) {
            super(config);
        }

        public CustomScorer(ScorerConfig config, MergeCheckerScoreStrategy strategy) {
            super(config, strategy);
        }

        @Override
        public List<Checker> prepareCheckers(DataItem dataItem) {
            return ListUtils.of(
                    new StarChecker(
                            CheckerConfig.builder().name("StarChecker").star(true).totalScore(2).build()
                    ),
                    new NormalChecker(
                            CheckerConfig.builder().name("NormalChecker").star(false).totalScore(2).build()
                    )
            );
        }
    }

    @Test
    void test() {
        Begin begin = new Begin(
                BeginConfig.builder()
                        .threshold(0.5)
                        .scoreStrategy(new AvgScoreRateStrategy())
                        .build()
        );

        DataLoader dataLoader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() throws Exception {
                return ListUtils.of(
                        new InputData(MapUtils.of("query", "1"))
                );
            }
        };

        CustomScorer customScorer = new CustomScorer(
                ScorerConfig.builder()
                        .metricName("customScorer")
                        .threshold(0.5)
                        .build()
        );

        String fileName = "MultiCheckerBasedScorerTest_" + DateUtils.nowToString("yyyyMMdd_HHmmss");
        HtmlReporter htmlReporter = new HtmlReporter(fileName, fileName);

        new WorkflowBuilder().link(begin, dataLoader, customScorer, htmlReporter).build().execute();
    }
}