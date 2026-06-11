package com.evalkit.framework.eval.node.begin;

import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.node.begin.config.BeginConfig;
import com.evalkit.framework.eval.node.scorer.strategy.*;
import com.evalkit.framework.infra.service.llm.LLMService;
import com.evalkit.framework.workflow.model.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@DisplayName("Begin 单元测试")
class BeginTest {

    /**
     * 构造一个最简 mock LLMService
     */
    private LLMService mockLlmService() {
        return new LLMService() {
            @Override
            public String chat(String prompt) {
                return "mock-response";
            }

            @Override
            public String getModel() {
                return "mock-model";
            }
        };
    }

    /**
     * 为 Begin 注入 WorkflowContext 并执行
     */
    private WorkflowContext executeWithContext(Begin begin) {
        WorkflowContext ctx = new WorkflowContext();
        begin.setWorkflowContext(ctx);
        try {
            begin.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ctx;
    }

    // ===================== constructor 测试 =====================

    @Test
    @DisplayName("无参构造器应使用默认 BeginConfig，不抛出异常")
    void testConstructor_defaultConfig() {
        Begin begin = new Begin();
        assertNotNull(begin.getConfig(), "默认构造器应初始化 config");
        assertNotNull(begin.getConfig().getScoreStrategy(), "默认 ScoreStrategy 不应为 null");
        assertNotNull(begin.getConfig().getEvalReasonStrategy(), "默认 EvalReasonStrategy 不应为 null");
    }

    @Test
    @DisplayName("带 BeginConfig 构造器应正确保存配置")
    void testConstructor_withConfig() {
        ScoreStrategy strategy = new AvgScoreStrategy();
        BeginConfig config = BeginConfig.builder().scoreStrategy(strategy).threshold(0.8).build();
        Begin begin = new Begin(config);
        assertSame(strategy, begin.getConfig().getScoreStrategy());
        assertEquals(0.8, begin.getConfig().getThreshold(), 1e-9);
    }

    // ===================== validConfig 测试 =====================

    @Test
    @DisplayName("config 为 null 时应抛出 IllegalArgumentException")
    void testValidConfig_nullConfigThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Begin(null),
                "config 为 null 时应抛异常");
    }

    @Test
    @DisplayName("ScoreStrategy 为 null 时应抛出 IllegalArgumentException")
    void testValidConfig_nullScoreStrategyThrows() {
        BeginConfig config = BeginConfig.builder().scoreStrategy(null).build();
        assertThrows(IllegalArgumentException.class, () -> new Begin(config),
                "ScoreStrategy 为 null 时应抛异常");
    }

    @Test
    @DisplayName("EvalReasonStrategy 为 null 时应抛出 IllegalArgumentException")
    void testValidConfig_nullEvalReasonStrategyThrows() {
        BeginConfig config = BeginConfig.builder().evalReasonStrategy(null).build();
        assertThrows(IllegalArgumentException.class, () -> new Begin(config),
                "EvalReasonStrategy 为 null 时应抛异常");
    }

    @Test
    @DisplayName("LLMSummaryEvalReasonStrategy 中 LLMService 为 null 时应抛出 IllegalArgumentException")
    void testValidConfig_llmStrategyWithNullLlmServiceThrows() {
        LLMSummaryEvalReasonStrategy strategy = new LLMSummaryEvalReasonStrategy(null, "some-prompt");
        BeginConfig config = BeginConfig.builder().evalReasonStrategy(strategy).build();
        assertThrows(IllegalArgumentException.class, () -> new Begin(config),
                "LLMSummaryEvalReasonStrategy LLMService 为 null 时应抛异常");
    }

    @Test
    @DisplayName("LLMSummaryEvalReasonStrategy 中 sysPrompt 为空时应抛出 IllegalArgumentException")
    void testValidConfig_llmStrategyWithEmptySysPromptThrows() {
        LLMSummaryEvalReasonStrategy strategy = new LLMSummaryEvalReasonStrategy(mockLlmService(), "");
        BeginConfig config = BeginConfig.builder().evalReasonStrategy(strategy).build();
        assertThrows(IllegalArgumentException.class, () -> new Begin(config),
                "LLMSummaryEvalReasonStrategy sysPrompt 为空时应抛异常");
    }

    @Test
    @DisplayName("LLMSummaryEvalReasonStrategy 配置合法时不应抛出异常")
    void testValidConfig_llmStrategyValid() {
        LLMSummaryEvalReasonStrategy strategy = new LLMSummaryEvalReasonStrategy(mockLlmService(), "valid-prompt");
        BeginConfig config = BeginConfig.builder().evalReasonStrategy(strategy).build();
        assertDoesNotThrow(() -> new Begin(config),
                "LLMSummaryEvalReasonStrategy 配置合法时不应抛异常");
    }

    // ===================== initWorkflowContext 测试 =====================

    @Test
    @DisplayName("执行后 WorkflowContext 中的 ScoreStrategy 应与配置一致")
    void testInitWorkflowContext_scorerStrategySet() {
        ScoreStrategy strategy = new AvgScoreStrategy();
        Begin begin = new Begin(BeginConfig.builder().scoreStrategy(strategy).build());
        WorkflowContext ctx = executeWithContext(begin);
        assertSame(strategy, WorkflowContextOps.getScorerStrategy(ctx),
                "上下文中的 ScoreStrategy 应与配置一致");
    }

    @Test
    @DisplayName("执行后 WorkflowContext 中的 EvalReasonStrategy 应与配置一致")
    void testInitWorkflowContext_evalReasonStrategySet() {
        EvalReasonStrategy reason = new JsonEvalReasonStrategy();
        Begin begin = new Begin(BeginConfig.builder().evalReasonStrategy(reason).build());
        WorkflowContext ctx = executeWithContext(begin);
        assertSame(reason, WorkflowContextOps.getEvalReasonStrategy(ctx),
                "上下文中的 EvalReasonStrategy 应与配置一致");
    }

    @Test
    @DisplayName("执行后 WorkflowContext 中的 threshold 应与配置一致")
    void testInitWorkflowContext_thresholdSet() {
        double threshold = 0.75;
        Begin begin = new Begin(BeginConfig.builder().threshold(threshold).build());
        WorkflowContext ctx = executeWithContext(begin);
        assertEquals(threshold, WorkflowContextOps.getThreshold(ctx), 1e-9,
                "上下文中的 threshold 应与配置一致");
    }

    @Test
    @DisplayName("执行后 WorkflowContext 中的 dataItems 应被初始化为非 null 空列表")
    void testInitWorkflowContext_dataItemsInitialized() {
        Begin begin = new Begin();
        WorkflowContext ctx = executeWithContext(begin);
        List<?> dataItems = WorkflowContextOps.getDataItems(ctx);
        assertNotNull(dataItems, "dataItems 不应为 null");
        assertTrue(dataItems.isEmpty(), "初始化后 dataItems 应为空列表");
    }

    @Test
    @DisplayName("若 WorkflowContext 中已有 dataItems，执行后不应覆盖原有数据")
    void testInitWorkflowContext_existingDataItemsNotOverwritten() {
        Begin begin = new Begin();
        WorkflowContext ctx = new WorkflowContext();
        // 预先写入非空 dataItems
        java.util.List<com.evalkit.framework.eval.model.DataItem> existing = new java.util.concurrent.CopyOnWriteArrayList<>();
        existing.add(new com.evalkit.framework.eval.model.DataItem(0L, null));
        WorkflowContextOps.setDataItems(ctx, existing);
        begin.setWorkflowContext(ctx);
        try {
            begin.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        List<?> dataItems = WorkflowContextOps.getDataItems(ctx);
        assertEquals(1, dataItems.size(), "已有 dataItems 时不应被清空");
    }

    @Test
    @DisplayName("执行后 WorkflowContext 中的 countResults 应被初始化为非 null 空 Map")
    void testInitWorkflowContext_countResultsInitialized() {
        Begin begin = new Begin();
        WorkflowContext ctx = executeWithContext(begin);
        java.util.Map<?, ?> countResults = WorkflowContextOps.getCountResults(ctx);
        assertNotNull(countResults, "countResults 不应为 null");
        assertTrue(countResults.isEmpty(), "初始化后 countResults 应为空 Map");
    }

    @Test
    @DisplayName("执行后 WorkflowContext 中的 extra 应被初始化为非 null 空 Map")
    void testInitWorkflowContext_extraInitialized() {
        Begin begin = new Begin();
        WorkflowContext ctx = executeWithContext(begin);
        java.util.Map<?, ?> extra = WorkflowContextOps.getExtra(ctx);
        assertNotNull(extra, "extra 不应为 null");
        assertTrue(extra.isEmpty(), "初始化后 extra 应为空 Map");
    }

    @Test
    @DisplayName("threshold 默认值为 0")
    void testInitWorkflowContext_defaultThresholdIsZero() {
        Begin begin = new Begin();
        WorkflowContext ctx = executeWithContext(begin);
        assertEquals(0d, WorkflowContextOps.getThreshold(ctx), 1e-9,
                "未指定 threshold 时默认值应为 0");
    }

    // ===================== 不同 ScoreStrategy 验证 =====================

    @Test
    @DisplayName("使用 SumScoreStrategy 时上下文中策略类型正确")
    void testWithSumScoreStrategy() {
        Begin begin = new Begin(BeginConfig.builder().scoreStrategy(new SumScoreStrategy()).build());
        WorkflowContext ctx = executeWithContext(begin);
        assertTrue(WorkflowContextOps.getScorerStrategy(ctx) instanceof SumScoreStrategy);
    }

    @Test
    @DisplayName("使用 MinScoreStrategy 时上下文中策略类型正确")
    void testWithMinScoreStrategy() {
        Begin begin = new Begin(BeginConfig.builder().scoreStrategy(new MinScoreStrategy()).build());
        WorkflowContext ctx = executeWithContext(begin);
        assertTrue(WorkflowContextOps.getScorerStrategy(ctx) instanceof MinScoreStrategy);
    }

    @Test
    @DisplayName("使用 NormalEvalReasonStrategy 时上下文中策略类型正确")
    void testWithNormalEvalReasonStrategy() {
        Begin begin = new Begin(BeginConfig.builder().evalReasonStrategy(new NormalEvalReasonStrategy()).build());
        WorkflowContext ctx = executeWithContext(begin);
        assertTrue(WorkflowContextOps.getEvalReasonStrategy(ctx) instanceof NormalEvalReasonStrategy);
    }
}