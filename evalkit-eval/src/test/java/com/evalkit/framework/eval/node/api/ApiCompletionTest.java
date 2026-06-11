package com.evalkit.framework.eval.node.api;

import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.api.config.ApiCompletionConfig;
import com.evalkit.framework.workflow.model.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@DisplayName("ApiCompletion 单元测试")
class ApiCompletionTest {

    // ===================== 工具方法 =====================

    /**
     * 构建一个简单的 ApiCompletion 实现，invoke 固定返回给定结果
     */
    private ApiCompletion buildApiCompletion(ApiCompletionResult fixedResult) {
        return new ApiCompletion() {
            @Override
            protected ApiCompletionResult invoke(DataItem dataItem) {
                return fixedResult;
            }
        };
    }

    /**
     * 构建一个 invoke 抛出异常的 ApiCompletion 实现
     */
    private ApiCompletion buildThrowingApiCompletion(RuntimeException ex) {
        return new ApiCompletion() {
            @Override
            protected ApiCompletionResult invoke(DataItem dataItem) {
                throw ex;
            }
        };
    }

    /**
     * 构造包含指定条数 DataItem 的 WorkflowContext
     */
    private WorkflowContext buildContextWithDataItems(int size) {
        WorkflowContext ctx = new WorkflowContext();
        List<DataItem> items = new CopyOnWriteArrayList<>();
        for (int i = 0; i < size; i++) {
            Map<String, Object> inputItem = new HashMap<>();
            inputItem.put("id", i);
            DataItem dataItem = new DataItem((long) i, new InputData(inputItem));
            items.add(dataItem);
        }
        WorkflowContextOps.setDataItems(ctx, items);
        return ctx;
    }

    /**
     * 为 ApiCompletion 注入上下文并执行
     */
    private void executeWithContext(ApiCompletion api, WorkflowContext ctx) {
        api.setWorkflowContext(ctx);
        try {
            api.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ===================== constructor 测试 =====================

    @Test
    @DisplayName("无参构造器应使用默认 ApiCompletionConfig，不抛出异常")
    void testConstructor_defaultConfig() {
        ApiCompletion api = buildApiCompletion(new ApiCompletionResult(new LinkedHashMap<>()));
        assertNotNull(api.getConfig(), "默认构造器应初始化 config");
        assertEquals(1, api.getConfig().getThreadNum(), "默认线程数应为 1");
        assertEquals(120, api.getConfig().getTimeout(), "默认超时应为 120");
    }

    @Test
    @DisplayName("带 ApiCompletionConfig 构造器应正确保存配置")
    void testConstructor_withConfig() {
        ApiCompletionConfig config = ApiCompletionConfig.builder().threadNum(4).timeout(60).build();
        ApiCompletion api = new ApiCompletion(config) {
            @Override
            protected ApiCompletionResult invoke(DataItem dataItem) {
                return null;
            }
        };
        assertEquals(4, api.getConfig().getThreadNum());
        assertEquals(60, api.getConfig().getTimeout());
    }

    // ===================== invokeWrapper 测试 =====================

    @Test
    @DisplayName("invokeWrapper 正常调用时应返回带 dataIndex 的结果，且 success=true")
    void testInvokeWrapper_success() {
        Map<String, Object> resultItem = new HashMap<>();
        resultItem.put("answer", "ok");
        ApiCompletionResult fixedResult = new ApiCompletionResult(resultItem);

        ApiCompletion api = buildApiCompletion(fixedResult);
        DataItem dataItem = new DataItem(1L, new InputData(new HashMap<>()));

        ApiCompletionResult result = api.invokeWrapper(dataItem);

        assertNotNull(result, "返回结果不应为 null");
        assertEquals(1L, result.getDataIndex(), "dataIndex 应与 DataItem 一致");
        assertTrue(result.isSuccess(), "正常调用时 success 应为 true");
        assertEquals("ok", result.get("answer"), "resultItem 内容应与 invoke 返回一致");
    }

    @Test
    @DisplayName("invokeWrapper 调用耗时字段应被正确记录")
    void testInvokeWrapper_timeCostRecorded() {
        ApiCompletion api = buildApiCompletion(new ApiCompletionResult(new HashMap<>()));
        DataItem dataItem = new DataItem(0L, new InputData(new HashMap<>()));

        ApiCompletionResult result = api.invokeWrapper(dataItem);

        assertTrue(result.getStartTime() > 0, "startTime 应大于 0");
        assertTrue(result.getEndTime() >= result.getStartTime(), "endTime 应 >= startTime");
        assertTrue(result.getTimeCost() >= 0, "timeCost 应 >= 0");
    }

    @Test
    @DisplayName("invoke 返回 null 时，invokeWrapper 应返回 success=false 的结果")
    void testInvokeWrapper_invokeReturnsNull() {
        ApiCompletion api = buildApiCompletion(null);
        DataItem dataItem = new DataItem(2L, new InputData(new HashMap<>()));

        ApiCompletionResult result = api.invokeWrapper(dataItem);

        assertNotNull(result);
        assertFalse(result.isSuccess(), "invoke 返回 null 时 success 应为 false");
    }

    @Test
    @DisplayName("invoke 抛出异常时，invokeWrapper 应捕获异常并返回 success=false 的结果")
    void testInvokeWrapper_invokeThrows() {
        ApiCompletion api = buildThrowingApiCompletion(new RuntimeException("mock error"));
        DataItem dataItem = new DataItem(3L, new InputData(new HashMap<>()));

        ApiCompletionResult result = api.invokeWrapper(dataItem);

        assertNotNull(result, "invoke 抛异常后不应返回 null");
        assertFalse(result.isSuccess(), "invoke 抛异常时 success 应为 false");
    }

    @Test
    @DisplayName("DataItem 已有 apiCompletionResult 时，invokeWrapper 应直接返回已有结果，不重复调用")
    void testInvokeWrapper_skipWhenResultExists() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        ApiCompletion api = new ApiCompletion() {
            @Override
            protected ApiCompletionResult invoke(DataItem dataItem) {
                invoked.set(true);
                return new ApiCompletionResult(new HashMap<>());
            }
        };

        ApiCompletionResult existingResult = new ApiCompletionResult(new HashMap<>());
        existingResult.setDataIndex(5L);
        DataItem dataItem = new DataItem(5L, new InputData(new HashMap<>()));
        dataItem.setApiCompletionResult(existingResult);

        ApiCompletionResult result = api.invokeWrapper(dataItem);

        assertFalse(invoked.get(), "已有 apiCompletionResult 时不应再次调用 invoke");
        assertSame(existingResult, result, "应直接返回已有结果");
    }

    // ===================== 钩子方法测试 =====================

    @Test
    @DisplayName("beforeInvoke 钩子被调用，可修改 DataItem")
    void testBeforeInvoke_called() {
        AtomicBoolean beforeCalled = new AtomicBoolean(false);
        ApiCompletion api = new ApiCompletion() {
            @Override
            protected DataItem beforeInvoke(DataItem dataItem) {
                beforeCalled.set(true);
                return dataItem;
            }

            @Override
            protected ApiCompletionResult invoke(DataItem dataItem) {
                return new ApiCompletionResult(new HashMap<>());
            }
        };

        api.invokeWrapper(new DataItem(0L, new InputData(new HashMap<>())));
        assertTrue(beforeCalled.get(), "beforeInvoke 钩子应被调用");
    }

    @Test
    @DisplayName("afterInvoke 钩子被调用，可修改返回结果")
    void testAfterInvoke_called() {
        ApiCompletionResult modifiedResult = new ApiCompletionResult(new HashMap<>());
        modifiedResult.set("modified", true);

        ApiCompletion api = new ApiCompletion() {
            @Override
            protected ApiCompletionResult invoke(DataItem dataItem) {
                return new ApiCompletionResult(new HashMap<>());
            }

            @Override
            protected ApiCompletionResult afterInvoke(DataItem dataItem, ApiCompletionResult result) {
                return modifiedResult;
            }
        };

        ApiCompletionResult result = api.invokeWrapper(new DataItem(0L, new InputData(new HashMap<>())));
        assertSame(modifiedResult, result, "afterInvoke 返回的结果应被最终使用");
    }

    @Test
    @DisplayName("onErrorInvoke 钩子在 invoke 抛异常时被调用")
    void testOnErrorInvoke_called() {
        AtomicBoolean errorCalled = new AtomicBoolean(false);
        AtomicReference<Throwable> capturedError = new AtomicReference<>();

        ApiCompletion api = new ApiCompletion() {
            @Override
            protected ApiCompletionResult invoke(DataItem dataItem) {
                throw new RuntimeException("test-error");
            }

            @Override
            protected void onErrorInvoke(DataItem dataItem, Throwable e) {
                errorCalled.set(true);
                capturedError.set(e);
            }
        };

        api.invokeWrapper(new DataItem(0L, new InputData(new HashMap<>())));
        assertTrue(errorCalled.get(), "invoke 抛异常时 onErrorInvoke 应被调用");
        assertNotNull(capturedError.get(), "捕获的异常不应为 null");
        assertEquals("test-error", capturedError.get().getMessage());
    }

    // ===================== doExecute 测试 =====================

    @Test
    @DisplayName("doExecute 正常执行后，DataItem 应被设置 apiCompletionResult")
    void testDoExecute_resultsSetOnDataItems() {
        ApiCompletion api = new ApiCompletion() {
            @Override
            protected ApiCompletionResult invoke(DataItem dataItem) {
                Map<String, Object> item = new HashMap<>();
                item.put("result", "value-" + dataItem.getDataIndex());
                return new ApiCompletionResult(item);
            }
        };

        WorkflowContext ctx = buildContextWithDataItems(3);
        executeWithContext(api, ctx);

        List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
        for (DataItem dataItem : dataItems) {
            assertNotNull(dataItem.getApiCompletionResult(),
                    "每个 DataItem 都应有 apiCompletionResult");
            assertEquals("value-" + dataItem.getDataIndex(),
                    dataItem.getApiCompletionResult().get("result"),
                    "apiCompletionResult 内容应与 invoke 返回一致");
        }
    }

    @Test
    @DisplayName("doExecute 时 DataItem 列表为空，应抛出 EvalException")
    void testDoExecute_emptyDataItems_throws() {
        ApiCompletion api = buildApiCompletion(new ApiCompletionResult(new HashMap<>()));
        WorkflowContext ctx = new WorkflowContext();
        WorkflowContextOps.setDataItems(ctx, new CopyOnWriteArrayList<>());
        api.setWorkflowContext(ctx);

        assertThrows(RuntimeException.class, () -> {
            try {
                api.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, "DataItems 为空时应抛出异常");
    }

    @Test
    @DisplayName("doExecute 时部分 invoke 抛异常，其余 DataItem 仍应正常完成")
    void testDoExecute_partialFailure_othersSucceed() {
        ApiCompletion api = new ApiCompletion() {
            @Override
            protected ApiCompletionResult invoke(DataItem dataItem) {
                // 只有 dataIndex=1 的抛异常
                if (dataItem.getDataIndex() == 1L) {
                    throw new RuntimeException("mock failure");
                }
                Map<String, Object> item = new HashMap<>();
                item.put("ok", true);
                return new ApiCompletionResult(item);
            }
        };

        WorkflowContext ctx = buildContextWithDataItems(3);
        executeWithContext(api, ctx);

        List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
        // index=0 和 index=2 应成功
        assertTrue(dataItems.get(0).getApiCompletionResult().isSuccess());
        assertFalse(dataItems.get(1).getApiCompletionResult().isSuccess(),
                "invoke 失败的 DataItem 的 success 应为 false");
        assertTrue(dataItems.get(2).getApiCompletionResult().isSuccess());
    }

    @Test
    @DisplayName("doExecute 时 DataItem 已有 apiCompletionResult，不应被覆盖")
    void testDoExecute_existingResultNotOverwritten() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        ApiCompletion api = new ApiCompletion() {
            @Override
            protected ApiCompletionResult invoke(DataItem dataItem) {
                invoked.set(true);
                return new ApiCompletionResult(new HashMap<>());
            }
        };

        WorkflowContext ctx = new WorkflowContext();
        List<DataItem> items = new CopyOnWriteArrayList<>();
        DataItem dataItem = new DataItem(0L, new InputData(new HashMap<>()));
        ApiCompletionResult existing = new ApiCompletionResult(new HashMap<>());
        existing.setDataIndex(0L);
        existing.setSuccess(true);
        dataItem.setApiCompletionResult(existing);
        items.add(dataItem);
        WorkflowContextOps.setDataItems(ctx, items);

        executeWithContext(api, ctx);

        assertFalse(invoked.get(), "已有 apiCompletionResult 时不应调用 invoke");
        assertSame(existing, WorkflowContextOps.getDataItems(ctx).get(0).getApiCompletionResult(),
                "已有结果不应被覆盖");
    }

    @Test
    @DisplayName("doExecute 按 dataIndex 匹配结果，顺序无关")
    void testDoExecute_resultMatchedByDataIndex() {
        ApiCompletion api = new ApiCompletion() {
            @Override
            protected ApiCompletionResult invoke(DataItem dataItem) {
                Map<String, Object> item = new HashMap<>();
                item.put("idx", dataItem.getDataIndex());
                return new ApiCompletionResult(item);
            }
        };

        WorkflowContext ctx = buildContextWithDataItems(5);
        executeWithContext(api, ctx);

        List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
        for (DataItem dataItem : dataItems) {
            Long idx = (Long) dataItem.getApiCompletionResult().get("idx");
            assertEquals(dataItem.getDataIndex(), idx,
                    "apiCompletionResult 应按 dataIndex 正确匹配到对应的 DataItem");
        }
    }
}