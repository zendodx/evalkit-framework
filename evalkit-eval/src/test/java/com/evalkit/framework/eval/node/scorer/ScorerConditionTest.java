package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.EvalResult;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.begin.Begin;
import com.evalkit.framework.eval.node.begin.config.BeginConfig;
import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.eval.node.reporter.StdReporter;
import com.evalkit.framework.eval.node.scorer.config.ScorerConfig;
import com.evalkit.framework.eval.node.scorer.strategy.SumScoreStrategy;
import com.evalkit.framework.workflow.WorkflowBuilder;
import com.evalkit.framework.workflow.model.WorkflowContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 方案A：ScorerConfig.condition 场景路由条件的单元测试。
 *
 * <p>测试覆盖：
 * <ul>
 *   <li>{@link Scorer#shouldEval} 条件为 null 时始终执行</li>
 *   <li>{@link Scorer#shouldEval} 条件命中时执行，未命中时跳过</li>
 *   <li>{@link Scorer#buildSkipResult} 跳过结果的各字段正确性</li>
 *   <li>通过 WorkflowBuilder 的端到端集成：多 Scorer 按 scene 字段分流，互不干扰</li>
 *   <li>跳过结果的 totalScore=0 不影响汇总分数</li>
 *   <li>skipScore 自定义值被写入跳过结果</li>
 * </ul>
 * </p>
 */
@DisplayName("方案A - Scorer condition 场景条件过滤")
class ScorerConditionTest {

    // ───────────────────────── 辅助 Builder ─────────────────────────

    /**
     * 构造一个固定返回 returnScore 的简单 Scorer，可携带 condition
     */
    private Scorer buildScorer(String metric, double returnScore, double totalScore,
                               java.util.function.Function<DataItem, Boolean> condition) {
        ScorerConfig cfg = ScorerConfig.builder()
                .metricName(metric)
                .totalScore(totalScore)
                .condition(condition)
                .build();
        return new Scorer(cfg) {
            @Override
            public ScorerResult eval(DataItem dataItem) {
                return new ScorerResult(metric, returnScore, totalScore, "正常评估结果");
            }
        };
    }

    /**
     * 构造一个携带 scene 字段的 DataItem，并注入 WorkflowContext
     */
    private DataItem buildDataItem(long index, String scene, Scorer scorer) {
        WorkflowContext ctx = new WorkflowContext();
        WorkflowContextOps.setScorerStrategy(ctx, new SumScoreStrategy());
        WorkflowContextOps.setThreshold(ctx, 0.0);
        scorer.setWorkflowContext(ctx);

        InputData inputData = new InputData(index, MapUtils.of("scene", scene));
        DataItem item = new DataItem();
        item.setDataIndex(index);
        item.setInputData(inputData);
        return item;
    }

    // ═══════════════════════════════════════════════════════════════
    // shouldEval 方法
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("shouldEval")
    class ShouldEvalTest {

        @Test
        @DisplayName("condition=null 时对任意 DataItem 均返回 true")
        void condition_null_alwaysEval() {
            Scorer scorer = buildScorer("m", 1.0, 1.0, null);
            DataItem item = new DataItem();
            item.setDataIndex(1L);
            assertTrue(scorer.shouldEval(item));
        }

        @Test
        @DisplayName("condition 返回 true 时返回 true")
        void condition_matches_returnsTrue() {
            Scorer scorer = buildScorer("m", 1.0, 1.0,
                    item -> "chat".equals(item.getInputData().get("scene")));
            DataItem item = buildDataItem(1L, "chat", scorer);
            assertTrue(scorer.shouldEval(item));
        }

        @Test
        @DisplayName("condition 返回 false 时返回 false")
        void condition_notMatches_returnsFalse() {
            Scorer scorer = buildScorer("m", 1.0, 1.0,
                    item -> "chat".equals(item.getInputData().get("scene")));
            DataItem item = buildDataItem(1L, "search", scorer);
            assertFalse(scorer.shouldEval(item));
        }

        @Test
        @DisplayName("condition 返回 null 时视为 false（防御 NPE）")
        void condition_returnsNull_treatedAsFalse() {
            Scorer scorer = buildScorer("m", 1.0, 1.0, item -> null);
            DataItem item = new DataItem();
            item.setDataIndex(1L);
            assertFalse(scorer.shouldEval(item));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // buildSkipResult 方法
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("buildSkipResult")
    class BuildSkipResultTest {

        @Test
        @DisplayName("跳过结果的基本字段正确")
        void skipResult_basicFields() {
            Scorer scorer = buildScorer("指标A", 1.0, 1.0,
                    item -> "chat".equals(item.getInputData().get("scene")));
            DataItem item = buildDataItem(42L, "search", scorer);

            ScorerResult skipResult = scorer.buildSkipResult(item);

            assertEquals(42L, skipResult.getDataIndex());
            assertEquals("指标A", skipResult.getMetric());
            assertEquals(0.0, skipResult.getScore(), 1e-6);
            // totalScore=0 确保不影响汇总分数
            assertEquals(0.0, skipResult.getTotalScore(), 1e-6);
            assertEquals("skipped by condition", skipResult.getReason());
            assertTrue(skipResult.isSuccess());
            assertTrue(skipResult.isPass()); // 跳过不算失败
        }

        @Test
        @DisplayName("star 字段固定为 false（跳过结果不触发一票否决）")
        void skipResult_starIsFalse() {
            ScorerConfig cfg = ScorerConfig.builder()
                    .metricName("必过指标")
                    .star(true)  // config 中设置了 star
                    .condition(item -> false)
                    .build();
            Scorer scorer = new Scorer(cfg) {
                @Override
                public ScorerResult eval(DataItem dataItem) {
                    return new ScorerResult("必过指标", 1.0, 1.0, "");
                }
            };
            DataItem item = new DataItem();
            item.setDataIndex(1L);

            ScorerResult skipResult = scorer.buildSkipResult(item);
            // 跳过结果的 star=false，不会触发一票否决
            assertFalse(skipResult.isStar());
        }

        @Test
        @DisplayName("skipScore 自定义值被写入跳过结果")
        void skipResult_customSkipScore() {
            ScorerConfig cfg = ScorerConfig.builder()
                    .metricName("m")
                    .condition(item -> false)
                    .skipScore(0.5)
                    .build();
            Scorer scorer = new Scorer(cfg) {
                @Override
                public ScorerResult eval(DataItem dataItem) {
                    return new ScorerResult("m", 1.0, 1.0, "");
                }
            };
            DataItem item = new DataItem();
            item.setDataIndex(1L);

            ScorerResult skipResult = scorer.buildSkipResult(item);
            assertEquals(0.5, skipResult.getScore(), 1e-6);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // evalWrapper 集成 condition 过滤
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("evalWrapper 集成 condition")
    class EvalWrapperWithConditionTest {

        @Test
        @DisplayName("条件命中时，正常执行评估并返回评估结果")
        void evalWrapper_conditionMatches_executesNormally() {
            Scorer scorer = buildScorer("对话质量", 0.9, 1.0,
                    item -> "chat".equals(item.getInputData().get("scene")));
            DataItem item = buildDataItem(1L, "chat", scorer);

            ScorerResult result = scorer.evalWrapper(item);

            assertTrue(result.isSuccess());
            assertEquals(0.9, result.getScore(), 1e-6);
            assertEquals("正常评估结果", result.getReason());
        }

        @Test
        @DisplayName("条件未命中时，doExecute 返回跳过结果（score=0, totalScore=0）")
        void evalWrapper_conditionNotMatches_doExecuteReturnsSkipResult() {
            // 注意：条件过滤在 doExecute 的调度层（shouldEval ? evalWrapper : buildSkipResult），
            // 不在 evalWrapper 本身。本测试通过 Workflow 端到端验证跳过行为。
            Scorer scorer = buildScorer("对话质量", 0.9, 1.0,
                    item -> "chat".equals(item.getInputData().get("scene")));

            Begin begin = new Begin(BeginConfig.builder()
                    .scoreStrategy(new SumScoreStrategy())
                    .build());

            DataLoader dataLoader = new DataLoader() {
                @Override
                public List<InputData> prepareDataList() {
                    // scene=search，不满足 condition（需要 chat）
                    return ListUtils.of(new InputData(MapUtils.of("scene", "search")));
                }
            };

            StdReporter reporter = new StdReporter();
            new WorkflowBuilder().link(begin, dataLoader, scorer, reporter).build().execute();

            WorkflowContext ctx = begin.getWorkflowContext();
            DataItem item = WorkflowContextOps.getDataItems(ctx).get(0);
            EvalResult evalResult = item.getEvalResult();

            // 条件未命中，跳过结果：score=0, totalScore=0
            ScorerResult skipResult = evalResult.getScorerResults().get(0);
            assertTrue(skipResult.isSuccess());
            assertTrue(skipResult.isPass());
            assertEquals(0.0, skipResult.getScore(), 1e-6);
            assertEquals(0.0, skipResult.getTotalScore(), 1e-6);
            assertEquals("skipped by condition", skipResult.getReason());
        }

        @Test
        @DisplayName("condition=null 时行为与无 condition 完全一致")
        void evalWrapper_nullCondition_behavesLikeNormal() {
            Scorer scorer = buildScorer("无条件", 1.0, 1.0, null);
            DataItem item = buildDataItem(1L, "any_scene", scorer);

            ScorerResult result = scorer.evalWrapper(item);

            assertTrue(result.isSuccess());
            assertEquals(1.0, result.getScore(), 1e-6);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 端到端集成测试：多 Scorer 按 scene 分流（通过 WorkflowBuilder）
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("端到端：多 Scorer 按 scene 场景分流")
    class EndToEndMultiSceneTest {

        /**
         * 数据集包含 chat/search/rag 三种场景各一条，
         * 三个 Scorer 分别只处理对应场景的 DataItem，
         * 验证：每个 DataItem 只被对应 Scorer 评估，跳过结果不影响最终分数。
         */
        @Test
        @DisplayName("三场景数据集，各 Scorer 只处理对应场景数据")
        void multiScene_eachScorerHandlesOwnScene() {
            Begin begin = new Begin(BeginConfig.builder()
                    .scoreStrategy(new SumScoreStrategy())
                    .threshold(0)
                    .build());

            DataLoader dataLoader = new DataLoader() {
                @Override
                public List<InputData> prepareDataList() {
                    return ListUtils.of(
                            new InputData(MapUtils.of("scene", "chat", "query", "你好")),
                            new InputData(MapUtils.of("scene", "search", "query", "搜索词")),
                            new InputData(MapUtils.of("scene", "rag", "query", "文档问题"))
                    );
                }
            };

            // chat 评估器：只处理 scene=chat，固定得分 0.8
            Scorer chatScorer = buildScorer("对话质量", 0.8, 1.0,
                    item -> "chat".equals(item.getInputData().get("scene")));

            // search 评估器：只处理 scene=search，固定得分 0.7
            Scorer searchScorer = buildScorer("搜索相关性", 0.7, 1.0,
                    item -> "search".equals(item.getInputData().get("scene")));

            // rag 评估器：只处理 scene=rag，固定得分 0.9
            Scorer ragScorer = buildScorer("RAG准确率", 0.9, 1.0,
                    item -> "rag".equals(item.getInputData().get("scene")));

            StdReporter reporter = new StdReporter();

            new WorkflowBuilder()
                    .link(begin, dataLoader, chatScorer, searchScorer, ragScorer, reporter)
                    .build()
                    .execute();

            // 通过 WorkflowContext 获取最终结果
            WorkflowContext ctx = begin.getWorkflowContext();
            List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
            assertThat(dataItems).hasSize(3);

            // 找到 chat 数据项
            DataItem chatItem = dataItems.stream()
                    .filter(d -> "chat".equals(d.getInputData().get("scene")))
                    .findFirst().orElseThrow(RuntimeException::new);
            EvalResult chatResult = chatItem.getEvalResult();
            // chat 数据项：chatScorer 得分0.8，searchScorer/ragScorer 跳过（totalScore=0不计入）
            // SumScoreStrategy 只计入 success=true 的 score，skip result score=0 + totalScore=0
            // 最终 score = 0.8 + 0 + 0 = 0.8（跳过的 totalScore=0，不影响归一化基准）
            assertThat(chatResult.getScore()).isCloseTo(0.8, org.assertj.core.data.Offset.offset(1e-6));
            // 验证 chat 数据项确实包含 chatScorer 的正常评估结果
            boolean hasChatScore = chatResult.getScorerResults().stream()
                    .anyMatch(r -> "对话质量".equals(r.getMetric()) && r.getScore() > 0);
            assertTrue(hasChatScore, "chat 数据项应包含对话质量评估结果");

            // 找到 search 数据项
            DataItem searchItem = dataItems.stream()
                    .filter(d -> "search".equals(d.getInputData().get("scene")))
                    .findFirst().orElseThrow(RuntimeException::new);
            EvalResult searchResult = searchItem.getEvalResult();
            assertThat(searchResult.getScore()).isCloseTo(0.7, org.assertj.core.data.Offset.offset(1e-6));

            // 找到 rag 数据项
            DataItem ragItem = dataItems.stream()
                    .filter(d -> "rag".equals(d.getInputData().get("scene")))
                    .findFirst().orElseThrow(RuntimeException::new);
            EvalResult ragResult = ragItem.getEvalResult();
            assertThat(ragResult.getScore()).isCloseTo(0.9, org.assertj.core.data.Offset.offset(1e-6));
        }

        @Test
        @DisplayName("同一数据项被多个 Scorer 评估时（无 condition），分数正常累加")
        void noCondition_allScorersEvaluateAllItems() {
            Begin begin = new Begin(BeginConfig.builder()
                    .scoreStrategy(new SumScoreStrategy())
                    .build());

            DataLoader dataLoader = new DataLoader() {
                @Override
                public List<InputData> prepareDataList() {
                    return ListUtils.of(new InputData(MapUtils.of("query", "测试")));
                }
            };

            // 两个无 condition 的 Scorer，分别得 0.6 和 0.4
            Scorer scorer1 = buildScorer("指标1", 0.6, 1.0, null);
            Scorer scorer2 = buildScorer("指标2", 0.4, 1.0, null);
            StdReporter reporter = new StdReporter();

            new WorkflowBuilder()
                    .link(begin, dataLoader, scorer1, scorer2, reporter)
                    .build()
                    .execute();

            WorkflowContext ctx = begin.getWorkflowContext();
            DataItem item = WorkflowContextOps.getDataItems(ctx).get(0);
            // SumScoreStrategy: 0.6 + 0.4 = 1.0
            assertThat(item.getEvalResult().getScore()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        }

        @Test
        @DisplayName("所有 Scorer 均未命中（全部跳过），最终分数为 0")
        void allScorersSkip_finalScoreIsZero() {
            Begin begin = new Begin(BeginConfig.builder()
                    .scoreStrategy(new SumScoreStrategy())
                    .build());

            DataLoader dataLoader = new DataLoader() {
                @Override
                public List<InputData> prepareDataList() {
                    return ListUtils.of(new InputData(MapUtils.of("scene", "unknown")));
                }
            };

            Scorer chatScorer = buildScorer("对话质量", 0.8, 1.0,
                    item -> "chat".equals(item.getInputData().get("scene")));
            Scorer searchScorer = buildScorer("搜索相关性", 0.7, 1.0,
                    item -> "search".equals(item.getInputData().get("scene")));
            StdReporter reporter = new StdReporter();

            new WorkflowBuilder()
                    .link(begin, dataLoader, chatScorer, searchScorer, reporter)
                    .build()
                    .execute();

            WorkflowContext ctx = begin.getWorkflowContext();
            DataItem item = WorkflowContextOps.getDataItems(ctx).get(0);
            // 两个 Scorer 都跳过，score=0+0=0
            assertThat(item.getEvalResult().getScore()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // AvgScoreRateStrategy 下的跳过验证（验证 totalScore=0 不影响均值）
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("跳过结果（totalScore=0）不影响整体得分（通过 Workflow 端到端验证）")
    void skipResult_doesNotInfluenceFinalScore() {
        // chat 场景：chatScorer 正常评 1.0，searchScorer 跳过（doExecute 层返回 totalScore=0）
        // 验证最终 EvalResult.score 只包含正常评估的分数
        Scorer chatScorer = buildScorer("对话质量", 1.0, 1.0,
                item -> "chat".equals(item.getInputData().get("scene")));
        Scorer searchScorer = buildScorer("搜索相关性", 0.5, 1.0,
                item -> "search".equals(item.getInputData().get("scene")));

        Begin begin = new Begin(BeginConfig.builder()
                .scoreStrategy(new SumScoreStrategy())
                .build());

        DataLoader dataLoader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                // 只有 chat 场景的一条数据
                return ListUtils.of(new InputData(MapUtils.of("scene", "chat")));
            }
        };

        StdReporter reporter = new StdReporter();
        new WorkflowBuilder().link(begin, dataLoader, chatScorer, searchScorer, reporter).build().execute();

        WorkflowContext ctx = begin.getWorkflowContext();
        DataItem item = WorkflowContextOps.getDataItems(ctx).get(0);
        List<ScorerResult> scorerResults = item.getEvalResult().getScorerResults();
        assertThat(scorerResults).hasSize(2);

        // chatScorer 正常评估，score=1.0，totalScore=1.0
        ScorerResult chatResult = scorerResults.stream()
                .filter(r -> "对话质量".equals(r.getMetric()) && !"skipped by condition".equals(r.getReason()))
                .findFirst().orElseThrow(RuntimeException::new);
        assertEquals(1.0, chatResult.getScore(), 1e-6);
        assertEquals(1.0, chatResult.getTotalScore(), 1e-6);

        // searchScorer 跳过，score=0.0，totalScore=0.0（不计入汇总基准）
        ScorerResult skipResult = scorerResults.stream()
                .filter(r -> "skipped by condition".equals(r.getReason()))
                .findFirst().orElseThrow(RuntimeException::new);
        assertEquals(0.0, skipResult.getScore(), 1e-6);
        assertEquals(0.0, skipResult.getTotalScore(), 1e-6);
        assertTrue(skipResult.isSuccess());
        assertTrue(skipResult.isPass());

        // SumScoreStrategy 最终分数 = 1.0（skip 的 score=0 不影响）
        assertThat(item.getEvalResult().getScore()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }
}

