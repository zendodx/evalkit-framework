package com.evalkit.framework.eval.node.api_wrapper;

import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.api_wrapper.config.ApiCompletionWrapperConfig;
import com.evalkit.framework.workflow.model.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@DisplayName("ApiCompletionWrapper 单元测试")
class ApiCompletionWrapperTest {
    
    // ===================== 工具方法 =====================

    /**
     * 构建一个 ApiCompletionWrapper，wrapper 逻辑由 Consumer 提供
     */
    private ApiCompletionWrapper buildWrapper(Consumer<DataItem> wrapperLogic) {
        return new ApiCompletionWrapper() {
            @Override
            protected void wrapper(DataItem dataItem) {
                wrapperLogic.accept(dataItem);
            }
        };
    }

    /**
     * 构建带自定义 config 的 ApiCompletionWrapper
     */
    private ApiCompletionWrapper buildWrapper(ApiCompletionWrapperConfig config,
                                              Consumer<DataItem> wrapperLogic) {
        return new ApiCompletionWrapper(config) {
            @Override
            protected void wrapper(DataItem dataItem) {
                wrapperLogic.accept(dataItem);
            }
        };
    }

    /**
     * 构造包含指定条数 DataItem（每条都带 ApiCompletionResult）的 WorkflowContext
     */
    private WorkflowContext buildContextWithDataItems(int size) {
        WorkflowContext ctx = new WorkflowContext();
        List<DataItem> items = new CopyOnWriteArrayList<>();
        for (int i = 0; i < size; i++) {
            Map<String, Object> inputItem = new HashMap<>();
            inputItem.put("id", i);
            DataItem dataItem = new DataItem((long) i, new InputData(inputItem));
            Map<String, Object> resultItem = new HashMap<>();
            resultItem.put("output", "raw-" + i);
            ApiCompletionResult result = new ApiCompletionResult(resultItem);
            result.setDataIndex((long) i);
            dataItem.setApiCompletionResult(result);
            items.add(dataItem);
        }
        WorkflowContextOps.setDataItems(ctx, items);
        return ctx;
    }

    /**
     * 为 ApiCompletionWrapper 注入上下文并执行
     */
    private void executeWithContext(ApiCompletionWrapper wrapper, WorkflowContext ctx) {
        wrapper.setWorkflowContext(ctx);
        try {
            wrapper.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ===================== constructor 测试 =====================

    @Test
    @DisplayName("无参构造器应使用默认 ApiCompletionWrapperConfig（threadNum=1）")
    void testConstructor_defaultConfig() {
        ApiCompletionWrapper wrapper = buildWrapper(dataItem -> {
        });
        assertNotNull(wrapper.config, "默认构造器应初始化 config");
        assertEquals(1, wrapper.config.getThreadNum(), "默认线程数应为 1");
    }

    @Test
    @DisplayName("带 ApiCompletionWrapperConfig 构造器应正确保存配置")
    void testConstructor_withConfig() {
        ApiCompletionWrapperConfig config = ApiCompletionWrapperConfig.builder().threadNum(4).build();
        ApiCompletionWrapper wrapper = buildWrapper(config, dataItem -> {
        });
        assertEquals(4, wrapper.config.getThreadNum());
    }

    // ===================== executeWrapper 测试 =====================

    @Test
    @DisplayName("executeWrapper 正常执行时应返回同一个 DataItem 实例")
    void testExecuteWrapper_returnsSameDataItem() {
        ApiCompletionWrapper wrapper = buildWrapper(dataItem -> {
        });
        DataItem dataItem = new DataItem(0L, new InputData(new HashMap<>()));
        DataItem result = wrapper.executeWrapper(dataItem);
        assertSame(dataItem, result, "executeWrapper 应返回同一 DataItem 实例");
    }

    @Test
    @DisplayName("executeWrapper 中 wrapper 逻辑可修改 ApiCompletionResult 字段")
    void testExecuteWrapper_wrapperModifiesApiCompletionResult() {
        ApiCompletionWrapper wrapper = buildWrapper(dataItem -> {
            if (dataItem.getApiCompletionResult() != null) {
                dataItem.getApiCompletionResult().set("wrapped", true);
            }
        });

        DataItem dataItem = new DataItem(0L, new InputData(new HashMap<>()));
        dataItem.setApiCompletionResult(new ApiCompletionResult(new HashMap<>()));
        wrapper.executeWrapper(dataItem);

        assertEquals(true, dataItem.getApiCompletionResult().get("wrapped"),
                "wrapper 应能修改 ApiCompletionResult 字段");
    }

    @Test
    @DisplayName("executeWrapper 中 wrapper 抛出异常时应被捕获，返回原 DataItem 不抛出")
    void testExecuteWrapper_wrapperThrows_returnOriginalItem() {
        ApiCompletionWrapper wrapper = buildWrapper(dataItem -> {
            throw new RuntimeException("mock wrapper error");
        });

        DataItem dataItem = new DataItem(0L, new InputData(new HashMap<>()));
        DataItem result = assertDoesNotThrow(() -> wrapper.executeWrapper(dataItem),
                "wrapper 抛异常时 executeWrapper 不应向外抛出");
        assertSame(dataItem, result, "抛异常后应返回原始 DataItem");
    }

    // ===================== 钩子方法测试 =====================

    @Test
    @DisplayName("beforeWrapper 钩子应在 wrapper 前被调用")
    void testBeforeWrapper_called() {
        List<String> callOrder = new ArrayList<>();

        ApiCompletionWrapper wrapper = new ApiCompletionWrapper() {
            @Override
            protected void beforeWrapper(DataItem dataItem) {
                callOrder.add("before");
            }

            @Override
            protected void wrapper(DataItem dataItem) {
                callOrder.add("wrapper");
            }
        };

        wrapper.executeWrapper(new DataItem(0L, new InputData(new HashMap<>())));
        assertEquals(Arrays.asList("before", "wrapper"), callOrder,
                "before 应在 wrapper 之前调用");
    }

    @Test
    @DisplayName("afterWrapper 钩子应在 wrapper 后被调用")
    void testAfterWrapper_called() {
        List<String> callOrder = new ArrayList<>();

        ApiCompletionWrapper wrapper = new ApiCompletionWrapper() {
            @Override
            protected void wrapper(DataItem dataItem) {
                callOrder.add("wrapper");
            }

            @Override
            protected void afterWrapper(DataItem dataItem) {
                callOrder.add("after");
            }
        };

        wrapper.executeWrapper(new DataItem(0L, new InputData(new HashMap<>())));
        assertEquals(Arrays.asList("wrapper", "after"), callOrder,
                "after 应在 wrapper 之后调用");
    }

    @Test
    @DisplayName("三个钩子按 before→wrapper→after 顺序执行")
    void testExecuteWrapper_hookOrder() {
        List<String> order = new ArrayList<>();

        ApiCompletionWrapper wrapper = new ApiCompletionWrapper() {
            @Override
            protected void beforeWrapper(DataItem dataItem) {
                order.add("before");
            }

            @Override
            protected void wrapper(DataItem dataItem) {
                order.add("wrapper");
            }

            @Override
            protected void afterWrapper(DataItem dataItem) {
                order.add("after");
            }
        };

        wrapper.executeWrapper(new DataItem(0L, new InputData(new HashMap<>())));
        assertEquals(Arrays.asList("before", "wrapper", "after"), order,
                "钩子应按 before→wrapper→after 顺序执行");
    }

    @Test
    @DisplayName("wrapper 抛异常时 onWrapperError 钩子被调用，并传入正确异常")
    void testOnWrapperError_called() {
        AtomicBoolean errorCalled = new AtomicBoolean(false);
        AtomicReference<Throwable> capturedError = new AtomicReference<>();

        ApiCompletionWrapper wrapper = new ApiCompletionWrapper() {
            @Override
            protected void wrapper(DataItem dataItem) {
                throw new RuntimeException("test-error");
            }

            @Override
            protected void onWrapperError(DataItem dataItem, Throwable e) {
                errorCalled.set(true);
                capturedError.set(e);
            }
        };

        wrapper.executeWrapper(new DataItem(0L, new InputData(new HashMap<>())));
        assertTrue(errorCalled.get(), "wrapper 抛异常时 onWrapperError 应被调用");
        assertNotNull(capturedError.get());
        assertEquals("test-error", capturedError.get().getMessage());
    }

    @Test
    @DisplayName("wrapper 抛异常时 afterWrapper 不被调用")
    void testAfterWrapper_notCalledOnError() {
        AtomicBoolean afterCalled = new AtomicBoolean(false);

        ApiCompletionWrapper wrapper = new ApiCompletionWrapper() {
            @Override
            protected void wrapper(DataItem dataItem) {
                throw new RuntimeException("error");
            }

            @Override
            protected void afterWrapper(DataItem dataItem) {
                afterCalled.set(true);
            }
        };

        wrapper.executeWrapper(new DataItem(0L, new InputData(new HashMap<>())));
        assertFalse(afterCalled.get(), "wrapper 抛异常时 afterWrapper 不应被调用");
    }

    // ===================== doExecute 测试 =====================

    @Test
    @DisplayName("doExecute 对 WorkflowContext 中的每个 DataItem 都应执行 wrapper")
    void testDoExecute_wrapperCalledForEachDataItem() {
        AtomicInteger wrapperCount = new AtomicInteger(0);
        ApiCompletionWrapper wrapper = buildWrapper(dataItem -> wrapperCount.incrementAndGet());

        WorkflowContext ctx = buildContextWithDataItems(5);
        executeWithContext(wrapper, ctx);

        assertEquals(5, wrapperCount.get(), "wrapper 应对每个 DataItem 都被调用一次");
    }

    @Test
    @DisplayName("doExecute 后 ApiCompletionResult 的修改应被持久化到 WorkflowContext")
    void testDoExecute_modificationsPersisted() {
        ApiCompletionWrapper wrapper = buildWrapper(dataItem -> {
            if (dataItem.getApiCompletionResult() != null) {
                dataItem.getApiCompletionResult().set("processed", true);
            }
        });

        WorkflowContext ctx = buildContextWithDataItems(3);
        executeWithContext(wrapper, ctx);

        List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
        for (DataItem dataItem : dataItems) {
            assertEquals(true, dataItem.getApiCompletionResult().get("processed"),
                    "每个 DataItem 的 ApiCompletionResult 都应包含 wrapper 写入的字段");
        }
    }

    @Test
    @DisplayName("doExecute 时 DataItem 列表为空，应抛出 EvalException")
    void testDoExecute_emptyDataItems_throws() {
        ApiCompletionWrapper wrapper = buildWrapper(dataItem -> {
        });
        WorkflowContext ctx = new WorkflowContext();
        WorkflowContextOps.setDataItems(ctx, new CopyOnWriteArrayList<>());
        wrapper.setWorkflowContext(ctx);

        assertThrows(RuntimeException.class, () -> {
            try {
                wrapper.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, "DataItems 为空时 doExecute 应抛出异常");
    }

    @Test
    @DisplayName("doExecute 时 DataItem 列表为 null，应抛出 EvalException")
    void testDoExecute_nullDataItems_throws() {
        ApiCompletionWrapper wrapper = buildWrapper(dataItem -> {
        });
        WorkflowContext ctx = new WorkflowContext();
        // 不设置 dataItems，默认为 null
        wrapper.setWorkflowContext(ctx);

        assertThrows(RuntimeException.class, () -> {
            try {
                wrapper.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, "DataItems 为 null 时 doExecute 应抛出异常");
    }

    @Test
    @DisplayName("doExecute 时部分 wrapper 抛异常，其余 DataItem 仍应正常处理")
    void testDoExecute_partialFailure_othersSucceed() {
        ApiCompletionWrapper wrapper = buildWrapper(dataItem -> {
            if (dataItem.getDataIndex() == 1L) {
                throw new RuntimeException("mock error");
            }
            dataItem.getApiCompletionResult().set("done", true);
        });

        WorkflowContext ctx = buildContextWithDataItems(3);
        executeWithContext(wrapper, ctx);

        List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
        assertEquals(true, dataItems.get(0).getApiCompletionResult().get("done"),
                "index=0 应正常完成");
        assertNull(dataItems.get(1).getApiCompletionResult().get("done"),
                "index=1 wrapper 失败，done 字段不应被设置");
        assertEquals(true, dataItems.get(2).getApiCompletionResult().get("done"),
                "index=2 应正常完成");
    }

    @Test
    @DisplayName("doExecute 对原始 output 字段进行覆写后，WorkflowContext 中的值应已更新")
    void testDoExecute_outputFieldOverwritten() {
        ApiCompletionWrapper wrapper = buildWrapper(dataItem -> {
            ApiCompletionResult result = dataItem.getApiCompletionResult();
            if (result != null) {
                String original = result.get("output");
                result.set("output", "wrapped-" + original);
            }
        });

        WorkflowContext ctx = buildContextWithDataItems(2);
        executeWithContext(wrapper, ctx);

        List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
        assertEquals("wrapped-raw-0", dataItems.get(0).getApiCompletionResult().get("output"));
        assertEquals("wrapped-raw-1", dataItems.get(1).getApiCompletionResult().get("output"));
    }
}