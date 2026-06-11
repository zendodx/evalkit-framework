package com.evalkit.framework.eval.node.dataloader_wrapper;

import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.dataloader_wrapper.config.DataLoaderWrapperConfig;
import com.evalkit.framework.workflow.model.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@DisplayName("DataLoaderWrapper 单元测试")
class DataLoaderWrapperTest {

    // ===================== 工具方法 =====================

    /**
     * 构建一个简单的 DataLoaderWrapper，wrapper 逻辑由 Runnable 提供
     */
    private DataLoaderWrapper buildWrapper(java.util.function.Consumer<DataItem> wrapperLogic) {
        return new DataLoaderWrapper() {
            @Override
            protected void wrapper(DataItem dataItem) {
                wrapperLogic.accept(dataItem);
            }
        };
    }

    /**
     * 构建带自定义 config 的 DataLoaderWrapper
     */
    private DataLoaderWrapper buildWrapper(DataLoaderWrapperConfig config,
                                           java.util.function.Consumer<DataItem> wrapperLogic) {
        return new DataLoaderWrapper(config) {
            @Override
            protected void wrapper(DataItem dataItem) {
                wrapperLogic.accept(dataItem);
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
            inputItem.put("value", "v" + i);
            items.add(new DataItem((long) i, new InputData(inputItem)));
        }
        WorkflowContextOps.setDataItems(ctx, items);
        return ctx;
    }

    /**
     * 为 DataLoaderWrapper 注入上下文并执行
     */
    private void executeWithContext(DataLoaderWrapper wrapper, WorkflowContext ctx) {
        wrapper.setWorkflowContext(ctx);
        try {
            wrapper.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ===================== constructor 测试 =====================

    @Test
    @DisplayName("无参构造器应使用默认 DataLoaderWrapperConfig（threadNum=1）")
    void testConstructor_defaultConfig() {
        DataLoaderWrapper wrapper = buildWrapper(dataItem -> {
        });
        assertNotNull(wrapper.config, "默认构造器应初始化 config");
        assertEquals(1, wrapper.config.getThreadNum(), "默认线程数应为 1");
    }

    @Test
    @DisplayName("带 DataLoaderWrapperConfig 构造器应正确保存配置")
    void testConstructor_withConfig() {
        DataLoaderWrapperConfig config = DataLoaderWrapperConfig.builder().threadNum(4).build();
        DataLoaderWrapper wrapper = buildWrapper(config, dataItem -> {
        });
        assertEquals(4, wrapper.config.getThreadNum());
    }

    // ===================== executeWrapper 测试 =====================

    @Test
    @DisplayName("executeWrapper 正常执行时应返回同一个 DataItem 实例")
    void testExecuteWrapper_returnsSameDataItem() {
        DataLoaderWrapper wrapper = buildWrapper(dataItem -> {
        });
        DataItem dataItem = new DataItem(0L, new InputData(new HashMap<>()));
        DataItem result = wrapper.executeWrapper(dataItem);
        assertSame(dataItem, result, "executeWrapper 应返回同一 DataItem 实例");
    }

    @Test
    @DisplayName("executeWrapper 中 wrapper 逻辑可修改 DataItem 的 InputData 字段")
    void testExecuteWrapper_wrapperModifiesDataItem() {
        DataLoaderWrapper wrapper = buildWrapper(dataItem ->
                dataItem.getInputData().set("modified", true));

        Map<String, Object> inputItem = new HashMap<>();
        DataItem dataItem = new DataItem(0L, new InputData(inputItem));
        wrapper.executeWrapper(dataItem);

        assertEquals(true, dataItem.getInputData().get("modified"),
                "wrapper 应能修改 DataItem 的 InputData 字段");
    }

    @Test
    @DisplayName("executeWrapper 中 wrapper 抛出异常时应被捕获，返回原 DataItem 不抛出")
    void testExecuteWrapper_wrapperThrows_returnOriginalItem() {
        DataLoaderWrapper wrapper = buildWrapper(dataItem -> {
            throw new RuntimeException("mock wrapper error");
        });

        DataItem dataItem = new DataItem(0L, new InputData(new HashMap<>()));
        DataItem result = assertDoesNotThrow(() -> wrapper.executeWrapper(dataItem),
                "wrapper 抛异常时 executeWrapper 不应向外抛出");
        assertSame(dataItem, result, "抛异常后应返回原始 DataItem");
    }

    // ===================== 钩子方法测试 =====================

    @Test
    @DisplayName("beforeWrapper 钩子在 wrapper 前被调用")
    void testBeforeWrapper_called() {
        AtomicBoolean beforeCalled = new AtomicBoolean(false);
        AtomicBoolean wrapperCalled = new AtomicBoolean(false);
        List<String> callOrder = new ArrayList<>();

        DataLoaderWrapper wrapper = new DataLoaderWrapper() {
            @Override
            protected void beforeWrapper(DataItem dataItem) {
                beforeCalled.set(true);
                callOrder.add("before");
            }

            @Override
            protected void wrapper(DataItem dataItem) {
                wrapperCalled.set(true);
                callOrder.add("wrapper");
            }
        };

        wrapper.executeWrapper(new DataItem(0L, new InputData(new HashMap<>())));
        assertTrue(beforeCalled.get(), "beforeWrapper 应被调用");
        assertEquals(Arrays.asList("before", "wrapper"), callOrder, "before 应在 wrapper 之前调用");
    }

    @Test
    @DisplayName("afterWrapper 钩子在 wrapper 后被调用")
    void testAfterWrapper_called() {
        List<String> callOrder = new ArrayList<>();

        DataLoaderWrapper wrapper = new DataLoaderWrapper() {
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
        assertEquals(Arrays.asList("wrapper", "after"), callOrder, "after 应在 wrapper 之后调用");
    }

    @Test
    @DisplayName("wrapper 抛异常时 onWrapperError 钩子被调用，并传入正确异常")
    void testOnWrapperError_called() {
        AtomicBoolean errorCalled = new AtomicBoolean(false);
        AtomicReference<Throwable> capturedError = new AtomicReference<>();

        DataLoaderWrapper wrapper = new DataLoaderWrapper() {
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

        DataLoaderWrapper wrapper = new DataLoaderWrapper() {
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
        DataLoaderWrapper wrapper = buildWrapper(dataItem -> wrapperCount.incrementAndGet());

        WorkflowContext ctx = buildContextWithDataItems(5);
        executeWithContext(wrapper, ctx);

        assertEquals(5, wrapperCount.get(), "wrapper 应对每个 DataItem 都被调用一次");
    }

    @Test
    @DisplayName("doExecute 后 DataItem 中的修改应被持久化到 WorkflowContext")
    void testDoExecute_modificationsPersisted() {
        DataLoaderWrapper wrapper = buildWrapper(dataItem ->
                dataItem.getInputData().set("wrapped", true));

        WorkflowContext ctx = buildContextWithDataItems(3);
        executeWithContext(wrapper, ctx);

        List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
        for (DataItem dataItem : dataItems) {
            assertEquals(true, dataItem.getInputData().get("wrapped"),
                    "每个 DataItem 的 InputData 都应包含 wrapper 写入的字段");
        }
    }

    @Test
    @DisplayName("doExecute 时部分 wrapper 抛异常，其余 DataItem 仍应正常处理")
    void testDoExecute_partialFailure_othersSucceed() {
        DataLoaderWrapper wrapper = buildWrapper(dataItem -> {
            if (dataItem.getDataIndex() == 1L) {
                throw new RuntimeException("mock error");
            }
            dataItem.getInputData().set("done", true);
        });

        WorkflowContext ctx = buildContextWithDataItems(3);
        executeWithContext(wrapper, ctx);

        List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
        assertEquals(true, dataItems.get(0).getInputData().get("done"),
                "index=0 应正常完成");
        assertNull(dataItems.get(1).getInputData().get("done"),
                "index=1 wrapper 失败，done 字段不应被设置");
        assertEquals(true, dataItems.get(2).getInputData().get("done"),
                "index=2 应正常完成");
    }

    @Test
    @DisplayName("doExecute 时 DataItem 列表为 null，不应抛出异常")
    void testDoExecute_nullDataItems_noThrow() {
        DataLoaderWrapper wrapper = buildWrapper(dataItem -> {
        });
        WorkflowContext ctx = new WorkflowContext();
        // 不设置 dataItems，默认为 null
        assertDoesNotThrow(() -> executeWithContext(wrapper, ctx),
                "DataItems 为 null 时 doExecute 不应抛出异常");
    }

    @Test
    @DisplayName("doExecute 时 DataItem 列表为空，不应抛出异常")
    void testDoExecute_emptyDataItems_noThrow() {
        DataLoaderWrapper wrapper = buildWrapper(dataItem -> {
        });
        WorkflowContext ctx = new WorkflowContext();
        WorkflowContextOps.setDataItems(ctx, new CopyOnWriteArrayList<>());
        assertDoesNotThrow(() -> executeWithContext(wrapper, ctx),
                "DataItems 为空时 doExecute 不应抛出异常");
    }

    @Test
    @DisplayName("executeWrapper 三个钩子按 before→wrapper→after 顺序执行")
    void testExecuteWrapper_hookOrder() {
        List<String> order = new ArrayList<>();

        DataLoaderWrapper wrapper = new DataLoaderWrapper() {
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
}