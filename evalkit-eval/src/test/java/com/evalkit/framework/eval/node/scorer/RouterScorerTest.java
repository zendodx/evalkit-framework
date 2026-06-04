package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.begin.Begin;
import com.evalkit.framework.eval.node.begin.config.BeginConfig;
import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.eval.node.reporter.StdReporter;
import com.evalkit.framework.eval.node.scorer.config.RouterScorerConfig;
import com.evalkit.framework.eval.node.scorer.config.ScorerConfig;
import com.evalkit.framework.eval.node.scorer.model.ScorerRoute;
import com.evalkit.framework.eval.node.scorer.strategy.SumScoreStrategy;
import com.evalkit.framework.workflow.WorkflowBuilder;
import com.evalkit.framework.workflow.model.WorkflowContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 方案B：{@link RouterScorer} 路由评估器的单元测试。
 *
 * <p>测试覆盖：
 * <ul>
 *   <li>构造校验：routes 为空时抛出 IllegalArgumentException</li>
 *   <li>first-match 模式：命中第一条规则，后续规则不执行</li>
 *   <li>first-match 模式：无路由命中且无兜底，返回跳过结果</li>
 *   <li>first-match 模式：无路由命中但有兜底 Scorer，委托兜底执行</li>
 *   <li>match-all 模式：所有命中规则均执行，结果取平均</li>
 *   <li>match-all 模式：无命中时返回跳过结果</li>
 *   <li>{@link ScorerRoute#of} 工厂方法</li>
 *   <li>{@link ScorerRoute#matches} 逻辑</li>
 *   <li>端到端：三场景数据集，RouterScorer 单节点完成所有场景分流</li>
 * </ul>
 * </p>
 */
@DisplayName("方案B - RouterScorer 路由评估器")
class RouterScorerTest {

    // ───────────────────────── 辅助 Builder ─────────────────────────

    /**
     * 构造一个固定返回 returnScore 的简单 Scorer（不带 condition）
     */
    private Scorer fixedScorer(String metric, double returnScore, double totalScore) {
        ScorerConfig cfg = ScorerConfig.builder()
                .metricName(metric)
                .totalScore(totalScore)
                .build();
        return new Scorer(cfg) {
            @Override
            public ScorerResult eval(DataItem dataItem) {
                return new ScorerResult(metric, returnScore, totalScore, metric + " 评估结果");
            }
        };
    }

    /**
     * 构造带 WorkflowContext 的 DataItem
     */
    private DataItem buildDataItem(long index, String scene, Scorer scorer) {
        WorkflowContext ctx = new WorkflowContext();
        WorkflowContextOps.setScorerStrategy(ctx, new SumScoreStrategy());
        WorkflowContextOps.setThreshold(ctx, 0.0);
        scorer.setWorkflowContext(ctx);

        DataItem item = new DataItem();
        item.setDataIndex(index);
        item.setInputData(new InputData(index, MapUtils.of("scene", scene)));
        return item;
    }

    // ═══════════════════════════════════════════════════════════════
    // 构造校验
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("构造校验")
    class ConstructorValidationTest {

        @Test
        @DisplayName("routes 为 null 时抛出 IllegalArgumentException")
        void nullRoutes_throwsIllegalArgument() {
            assertThatThrownBy(() -> new RouterScorer(
                    RouterScorerConfig.builder()
                            .metricName("路由评估")
                            .routes(null)
                            .build()
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("routes");
        }

        @Test
        @DisplayName("routes 为空列表时抛出 IllegalArgumentException")
        void emptyRoutes_throwsIllegalArgument() {
            assertThatThrownBy(() -> new RouterScorer(
                    RouterScorerConfig.builder()
                            .metricName("路由评估")
                            .routes(java.util.Collections.emptyList())
                            .build()
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("routes");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ScorerRoute 工具方法
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ScorerRoute")
    class ScorerRouteTest {

        @Test
        @DisplayName("of() 工厂方法构造正确")
        void of_buildsRouteCorrectly() {
            Scorer scorer = fixedScorer("m", 1.0, 1.0);
            ScorerRoute route = ScorerRoute.of(item -> true, scorer, "测试路由");

            assertEquals("测试路由", route.getRouteName());
            assertNotNull(route.getCondition());
            assertSame(scorer, route.getScorer());
        }

        @Test
        @DisplayName("matches() 条件为 true 时返回 true")
        void matches_conditionTrue_returnsTrue() {
            ScorerRoute route = ScorerRoute.of(
                    item -> "chat".equals(item.getInputData().get("scene")),
                    fixedScorer("m", 1.0, 1.0),
                    "对话场景"
            );
            DataItem item = new DataItem();
            item.setInputData(new InputData(MapUtils.of("scene", "chat")));
            assertTrue(route.matches(item));
        }

        @Test
        @DisplayName("matches() 条件为 false 时返回 false")
        void matches_conditionFalse_returnsFalse() {
            ScorerRoute route = ScorerRoute.of(
                    item -> "chat".equals(item.getInputData().get("scene")),
                    fixedScorer("m", 1.0, 1.0),
                    "对话场景"
            );
            DataItem item = new DataItem();
            item.setInputData(new InputData(MapUtils.of("scene", "search")));
            assertFalse(route.matches(item));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // first-match 模式（默认）
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("first-match 模式（默认）")
    class FirstMatchModeTest {

        @Test
        @DisplayName("命中第一条规则，返回该规则的 Scorer 结果")
        void firstMatch_hitsFirstRoute_returnsFirstResult() throws Exception {
            Scorer chatScorer = fixedScorer("对话质量", 0.8, 1.0);
            Scorer searchScorer = fixedScorer("搜索相关性", 0.6, 1.0);

            RouterScorer router = new RouterScorer(RouterScorerConfig.builder()
                    .metricName("路由评估")
                    .routes(Arrays.asList(
                            ScorerRoute.of(item -> "chat".equals(item.getInputData().get("scene")), chatScorer, "对话"),
                            ScorerRoute.of(item -> "search".equals(item.getInputData().get("scene")), searchScorer, "搜索")
                    ))
                    .build());

            DataItem chatItem = buildDataItem(1L, "chat", router);
            ScorerResult result = router.eval(chatItem);

            assertEquals("对话质量", result.getMetric());
            assertEquals(0.8, result.getScore(), 1e-6);
            assertEquals("对话质量 评估结果", result.getReason());
        }

        @Test
        @DisplayName("命中第二条规则（第一条未命中），返回第二条规则的结果")
        void firstMatch_hitsSecondRoute_returnsSecondResult() throws Exception {
            Scorer chatScorer = fixedScorer("对话质量", 0.8, 1.0);
            Scorer searchScorer = fixedScorer("搜索相关性", 0.6, 1.0);

            RouterScorer router = new RouterScorer(RouterScorerConfig.builder()
                    .metricName("路由评估")
                    .routes(Arrays.asList(
                            ScorerRoute.of(item -> "chat".equals(item.getInputData().get("scene")), chatScorer, "对话"),
                            ScorerRoute.of(item -> "search".equals(item.getInputData().get("scene")), searchScorer, "搜索")
                    ))
                    .build());

            DataItem searchItem = buildDataItem(2L, "search", router);
            ScorerResult result = router.eval(searchItem);

            assertEquals("搜索相关性", result.getMetric());
            assertEquals(0.6, result.getScore(), 1e-6);
        }

        @Test
        @DisplayName("无路由命中且无兜底，返回跳过结果（score=0, totalScore=0）")
        void firstMatch_noMatchNoDefault_returnsSkipResult() throws Exception {
            Scorer chatScorer = fixedScorer("对话质量", 0.8, 1.0);

            RouterScorer router = new RouterScorer(RouterScorerConfig.builder()
                    .metricName("路由评估")
                    .routes(ListUtils.of(
                            ScorerRoute.of(item -> "chat".equals(item.getInputData().get("scene")), chatScorer, "对话")
                    ))
                    .build());

            DataItem unknownItem = buildDataItem(3L, "unknown", router);
            ScorerResult result = router.eval(unknownItem);

            assertEquals("skipped by condition", result.getReason());
            assertEquals(0.0, result.getScore(), 1e-6);
            assertEquals(0.0, result.getTotalScore(), 1e-6);
            assertTrue(result.isSuccess());
            assertTrue(result.isPass());
        }

        @Test
        @DisplayName("无路由命中但有兜底 Scorer，委托兜底执行")
        void firstMatch_noMatchWithDefault_delegatesToDefaultScorer() throws Exception {
            Scorer chatScorer = fixedScorer("对话质量", 0.8, 1.0);
            Scorer fallbackScorer = fixedScorer("兜底评估", 0.3, 1.0);

            RouterScorer router = new RouterScorer(RouterScorerConfig.builder()
                    .metricName("路由评估")
                    .routes(ListUtils.of(
                            ScorerRoute.of(item -> "chat".equals(item.getInputData().get("scene")), chatScorer, "对话")
                    ))
                    .defaultScorer(fallbackScorer)
                    .build());

            DataItem unknownItem = buildDataItem(4L, "unknown", router);
            ScorerResult result = router.eval(unknownItem);

            assertEquals("兜底评估", result.getMetric());
            assertEquals(0.3, result.getScore(), 1e-6);
        }

        @Test
        @DisplayName("多条规则均命中时，only 第一条规则生效（first-match 语义）")
        void firstMatch_multipleRoutesMatch_onlyFirstTaken() throws Exception {
            Scorer scorer1 = fixedScorer("指标1", 0.9, 1.0);
            Scorer scorer2 = fixedScorer("指标2", 0.5, 1.0);

            RouterScorer router = new RouterScorer(RouterScorerConfig.builder()
                    .metricName("路由评估")
                    .routes(Arrays.asList(
                            ScorerRoute.of(item -> true, scorer1, "全匹配1"),  // 始终命中
                            ScorerRoute.of(item -> true, scorer2, "全匹配2")   // 也始终命中
                    ))
                    .matchAll(false)
                    .build());

            DataItem item = buildDataItem(5L, "any", router);
            ScorerResult result = router.eval(item);

            // first-match: 只取第一条
            assertEquals("指标1", result.getMetric());
            assertEquals(0.9, result.getScore(), 1e-6);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // match-all 模式
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("match-all 模式")
    class MatchAllModeTest {

        @Test
        @DisplayName("多条规则均命中，结果取所有命中 Scorer 的平均分")
        void matchAll_allRouteMatch_returnsAvgScore() throws Exception {
            Scorer scorer1 = fixedScorer("指标1", 0.8, 1.0);
            Scorer scorer2 = fixedScorer("指标2", 0.6, 1.0);

            RouterScorer router = new RouterScorer(RouterScorerConfig.builder()
                    .metricName("多维评估")
                    .routes(Arrays.asList(
                            ScorerRoute.of(item -> true, scorer1, "维度1"),
                            ScorerRoute.of(item -> true, scorer2, "维度2")
                    ))
                    .matchAll(true)
                    .build());

            DataItem item = buildDataItem(1L, "any", router);
            ScorerResult result = router.eval(item);

            // 平均分 = (0.8 + 0.6) / 2 = 0.7
            assertEquals("多维评估", result.getMetric());
            assertThat(result.getScore()).isCloseTo(0.7, org.assertj.core.data.Offset.offset(1e-6));
        }

        @Test
        @DisplayName("只有部分规则命中，只对命中规则求平均")
        void matchAll_partialMatch_averagesMatchedOnly() throws Exception {
            Scorer scorer1 = fixedScorer("指标1", 1.0, 1.0);
            Scorer scorer2 = fixedScorer("指标2", 0.0, 1.0);

            RouterScorer router = new RouterScorer(RouterScorerConfig.builder()
                    .metricName("部分匹配")
                    .routes(Arrays.asList(
                            ScorerRoute.of(item -> "chat".equals(item.getInputData().get("scene")), scorer1, "对话"),
                            ScorerRoute.of(item -> "search".equals(item.getInputData().get("scene")), scorer2, "搜索")
                    ))
                    .matchAll(true)
                    .build());

            // scene=chat 只命中第一条规则
            DataItem chatItem = buildDataItem(1L, "chat", router);
            ScorerResult result = router.eval(chatItem);

            // 只有 scorer1 命中，score = 1.0
            assertThat(result.getScore()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        }

        @Test
        @DisplayName("match-all 无命中时返回跳过结果")
        void matchAll_noMatch_returnsSkipResult() throws Exception {
            Scorer scorer = fixedScorer("指标", 1.0, 1.0);

            RouterScorer router = new RouterScorer(RouterScorerConfig.builder()
                    .metricName("无命中")
                    .routes(ListUtils.of(
                            ScorerRoute.of(item -> "chat".equals(item.getInputData().get("scene")), scorer, "对话")
                    ))
                    .matchAll(true)
                    .build());

            DataItem item = buildDataItem(1L, "unknown", router);
            ScorerResult result = router.eval(item);

            assertEquals("skipped by condition", result.getReason());
            assertEquals(0.0, result.getTotalScore(), 1e-6);
        }

        @Test
        @DisplayName("match-all 理由拼接了所有命中路由的 metric 和 reason")
        void matchAll_reasonContainsAllMatchedMetrics() throws Exception {
            Scorer scorer1 = fixedScorer("指标1", 0.9, 1.0);
            Scorer scorer2 = fixedScorer("指标2", 0.7, 1.0);

            RouterScorer router = new RouterScorer(RouterScorerConfig.builder()
                    .metricName("多维路由")
                    .routes(Arrays.asList(
                            ScorerRoute.of(item -> true, scorer1, "维度1"),
                            ScorerRoute.of(item -> true, scorer2, "维度2")
                    ))
                    .matchAll(true)
                    .build());

            DataItem item = buildDataItem(1L, "any", router);
            ScorerResult result = router.eval(item);

            assertThat(result.getReason()).contains("指标1").contains("指标2");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // workflowContext 传递校验
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("workflowContext 传递")
    class WorkflowContextPropagationTest {

        @Test
        @DisplayName("子 Scorer 在 eval 时可访问 RouterScorer 的 workflowContext")
        void subScorer_receivesWorkflowContext() throws Exception {
            // 子 Scorer 通过 getWorkflowContext() 读取 threshold 做断言
            final double[] capturedThreshold = {-1};
            ScorerConfig cfg = ScorerConfig.builder().metricName("上下文校验").totalScore(1.0).build();
            Scorer contextAwareScorer = new Scorer(cfg) {
                @Override
                public ScorerResult eval(DataItem dataItem) {
                    capturedThreshold[0] = WorkflowContextOps.getThreshold(getWorkflowContext());
                    return new ScorerResult("上下文校验", 1.0, 1.0, "OK");
                }
            };

            RouterScorer router = new RouterScorer(RouterScorerConfig.builder()
                    .metricName("路由评估")
                    .routes(ListUtils.of(
                            ScorerRoute.of(item -> true, contextAwareScorer, "全匹配")
                    ))
                    .build());

            WorkflowContext ctx = new WorkflowContext();
            WorkflowContextOps.setScorerStrategy(ctx, new SumScoreStrategy());
            WorkflowContextOps.setThreshold(ctx, 0.75); // 设置特定阈值
            router.setWorkflowContext(ctx);

            DataItem item = new DataItem();
            item.setDataIndex(1L);
            item.setInputData(new InputData(MapUtils.of("x", "y")));

            router.eval(item);

            // 验证子 Scorer 拿到了正确的 threshold
            assertEquals(0.75, capturedThreshold[0], 1e-6);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 端到端集成测试（通过 WorkflowBuilder）
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("端到端：RouterScorer + WorkflowBuilder")
    class EndToEndTest {

        /**
         * 数据集包含 chat/search/rag 三种场景各一条，
         * RouterScorer 单节点通过 first-match 完成分流。
         * 验证每个 DataItem 的 EvalResult 分数来自对应场景的 Scorer。
         */
        @Test
        @DisplayName("三场景数据集，单个 RouterScorer 节点完成所有场景分流")
        void endToEnd_threeScenes_singleRouterNode() {
            Scorer chatScorer = fixedScorer("对话质量", 0.8, 1.0);
            Scorer searchScorer = fixedScorer("搜索相关性", 0.7, 1.0);
            Scorer ragScorer = fixedScorer("RAG准确率", 0.9, 1.0);

            RouterScorer router = new RouterScorer(RouterScorerConfig.builder()
                    .metricName("场景路由评估")
                    .routes(Arrays.asList(
                            ScorerRoute.of(item -> "chat".equals(item.getInputData().get("scene")), chatScorer, "对话场景"),
                            ScorerRoute.of(item -> "search".equals(item.getInputData().get("scene")), searchScorer, "搜索场景"),
                            ScorerRoute.of(item -> "rag".equals(item.getInputData().get("scene")), ragScorer, "RAG场景")
                    ))
                    .build());

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

            StdReporter reporter = new StdReporter();
            new WorkflowBuilder().link(begin, dataLoader, router, reporter).build().execute();

            WorkflowContext ctx = begin.getWorkflowContext();
            List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
            assertThat(dataItems).hasSize(3);

            DataItem chatItem = dataItems.stream()
                    .filter(d -> "chat".equals(d.getInputData().get("scene")))
                    .findFirst().orElseThrow(RuntimeException::new);
            assertThat(chatItem.getEvalResult().getScore()).isCloseTo(0.8, org.assertj.core.data.Offset.offset(1e-6));
            // 验证 metric 是对话质量（由 chatScorer 的结果写入）
            assertThat(chatItem.getEvalResult().getScorerResults().get(0).getMetric()).isEqualTo("对话质量");

            DataItem searchItem = dataItems.stream()
                    .filter(d -> "search".equals(d.getInputData().get("scene")))
                    .findFirst().orElseThrow(RuntimeException::new);
            assertThat(searchItem.getEvalResult().getScore()).isCloseTo(0.7, org.assertj.core.data.Offset.offset(1e-6));

            DataItem ragItem = dataItems.stream()
                    .filter(d -> "rag".equals(d.getInputData().get("scene")))
                    .findFirst().orElseThrow(RuntimeException::new);
            assertThat(ragItem.getEvalResult().getScore()).isCloseTo(0.9, org.assertj.core.data.Offset.offset(1e-6));
        }

        @Test
        @DisplayName("RouterScorer + 通用 Scorer 串联：通用 Scorer 对所有 DataItem 生效，路由 Scorer 按场景分流")
        void endToEnd_routerPlusUniversalScorer() {
            // 通用 Scorer（无 condition）
            Scorer universalScorer = fixedScorer("通用格式检查", 0.5, 1.0);

            // 路由 Scorer
            Scorer chatScorer = fixedScorer("对话质量", 0.8, 1.0);
            Scorer searchScorer = fixedScorer("搜索相关性", 0.6, 1.0);
            RouterScorer router = new RouterScorer(RouterScorerConfig.builder()
                    .metricName("场景路由")
                    .routes(Arrays.asList(
                            ScorerRoute.of(item -> "chat".equals(item.getInputData().get("scene")), chatScorer, "对话"),
                            ScorerRoute.of(item -> "search".equals(item.getInputData().get("scene")), searchScorer, "搜索")
                    ))
                    .build());

            Begin begin = new Begin(BeginConfig.builder()
                    .scoreStrategy(new SumScoreStrategy())
                    .build());

            DataLoader dataLoader = new DataLoader() {
                @Override
                public List<InputData> prepareDataList() {
                    return ListUtils.of(
                            new InputData(MapUtils.of("scene", "chat")),
                            new InputData(MapUtils.of("scene", "search"))
                    );
                }
            };

            StdReporter reporter = new StdReporter();
            new WorkflowBuilder().link(begin, dataLoader, universalScorer, router, reporter).build().execute();

            WorkflowContext ctx = begin.getWorkflowContext();
            List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);

            DataItem chatItem = dataItems.stream()
                    .filter(d -> "chat".equals(d.getInputData().get("scene")))
                    .findFirst().orElseThrow(RuntimeException::new);
            // chat: universalScorer(0.5) + chatScorer(0.8) = 1.3
            assertThat(chatItem.getEvalResult().getScore()).isCloseTo(1.3, org.assertj.core.data.Offset.offset(1e-6));

            DataItem searchItem = dataItems.stream()
                    .filter(d -> "search".equals(d.getInputData().get("scene")))
                    .findFirst().orElseThrow(RuntimeException::new);
            // search: universalScorer(0.5) + searchScorer(0.6) = 1.1
            assertThat(searchItem.getEvalResult().getScore()).isCloseTo(1.1, org.assertj.core.data.Offset.offset(1e-6));
        }

        @Test
        @DisplayName("未知场景数据使用 defaultScorer 兜底")
        void endToEnd_unknownScene_defaultScorerApplied() {
            Scorer fallback = fixedScorer("兜底评估", 0.1, 1.0);
            Scorer chatScorer = fixedScorer("对话质量", 0.8, 1.0);

            RouterScorer router = new RouterScorer(RouterScorerConfig.builder()
                    .metricName("场景路由")
                    .routes(ListUtils.of(
                            ScorerRoute.of(item -> "chat".equals(item.getInputData().get("scene")), chatScorer, "对话")
                    ))
                    .defaultScorer(fallback)
                    .build());

            Begin begin = new Begin(BeginConfig.builder()
                    .scoreStrategy(new SumScoreStrategy())
                    .build());

            DataLoader dataLoader = new DataLoader() {
                @Override
                public List<InputData> prepareDataList() {
                    return ListUtils.of(new InputData(MapUtils.of("scene", "unknown")));
                }
            };

            StdReporter reporter = new StdReporter();
            new WorkflowBuilder().link(begin, dataLoader, router, reporter).build().execute();

            WorkflowContext ctx = begin.getWorkflowContext();
            DataItem item = WorkflowContextOps.getDataItems(ctx).get(0);
            // 未知场景命中 defaultScorer，分数=0.1
            assertThat(item.getEvalResult().getScore()).isCloseTo(0.1, org.assertj.core.data.Offset.offset(1e-6));
            assertThat(item.getEvalResult().getScorerResults().get(0).getMetric()).isEqualTo("兜底评估");
        }
    }
}

