package com.evalkit.framework.eval.node.api_wrapper;

import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.eval.constants.NodeNamePrefix;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.api_wrapper.config.ApiCompletionWrapperConfig;
import com.evalkit.framework.workflow.model.WorkflowContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiCompletionWrapper 单元测试
 * <p>
 * 测试覆盖：
 * <ul>
 *   <li>节点 ID 前缀规范</li>
 *   <li>doExecute 空数据保护</li>
 *   <li>executeWrapper 正常装饰流程（钩子顺序）</li>
 *   <li>executeWrapper 装饰异常时不影响整体、返回原数据项</li>
 *   <li>onWrapperError 在装饰异常时被调用</li>
 *   <li>多数据项并发装饰，单条失败不影响其他条</li>
 *   <li>wrapper 对 ApiCompletionResult 的修改正确回写</li>
 * </ul>
 */
@DisplayName("ApiCompletionWrapper")
class ApiCompletionWrapperTest {

    // ==================== 工厂方法 ====================

    /**
     * 构造一个正常执行的 wrapper，将 resultItem 中写入指定 key/value
     */
    private ApiCompletionWrapper buildWrapper(String writeKey, String writeValue) {
        return new ApiCompletionWrapper() {
            @Override
            protected void wrapper(DataItem dataItem) {
                ApiCompletionResult result = dataItem.getApiCompletionResult();
                if (result != null) {
                    result.set(writeKey, writeValue);
                }
            }
        };
    }

    /**
     * 构造一个在 wrapper 中抛出异常的 wrapper
     */
    private ApiCompletionWrapper buildThrowingWrapper(RuntimeException ex) {
        return new ApiCompletionWrapper() {
            @Override
            protected void wrapper(DataItem dataItem) {
                throw ex;
            }
        };
    }

    /**
     * 构造一个记录钩子调用顺序的 wrapper
     */
    private ApiCompletionWrapper buildHookOrderWrapper(List<String> callLog) {
        return new ApiCompletionWrapper() {
            @Override
            protected void beforeWrapper(DataItem dataItem) {
                callLog.add("before");
            }

            @Override
            protected void wrapper(DataItem dataItem) {
                callLog.add("wrapper");
            }

            @Override
            protected void afterWrapper(DataItem dataItem) {
                callLog.add("after");
            }
        };
    }

    /**
     * 构造一个记录 onWrapperError 的 wrapper
     */
    private ApiCompletionWrapper buildErrorCapturingWrapper(List<Throwable> errors) {
        return new ApiCompletionWrapper() {
            @Override
            protected void wrapper(DataItem dataItem) {
                throw new RuntimeException("故意抛出");
            }

            @Override
            protected void onWrapperError(DataItem dataItem, Throwable e) {
                errors.add(e);
            }
        };
    }

    /**
     * 构造最简 DataItem（含 ApiCompletionResult）
     */
    private DataItem buildDataItem(long index) {
        DataItem item = new DataItem();
        item.setDataIndex(index);
        Map<String, Object> input = new HashMap<>();
        input.put("query", "测试问题-" + index);
        item.setInputData(new InputData(index, input));
        Map<String, Object> result = new HashMap<>();
        result.put("answer", "测试回答-" + index);
        item.setApiCompletionResult(new ApiCompletionResult(result));
        return item;
    }

    /**
     * 通过 WorkflowContext 驱动 doExecute
     */
    private void executeWithContext(ApiCompletionWrapper wrapper, List<DataItem> dataItems) {
        WorkflowContext ctx = new WorkflowContext();
        WorkflowContextOps.setDataItems(ctx, dataItems);
        wrapper.setWorkflowContext(ctx);
        wrapper.doExecute();
    }

    // ==================== 节点 ID 规范 ====================

    @Nested
    @DisplayName("节点 ID")
    class NodeIdTest {

        @Test
        @DisplayName("节点 ID 应以 apiCompletionWrapper- 为前缀")
        void nodeId_startsWithCorrectPrefix() {
            ApiCompletionWrapper wrapper = buildWrapper("k", "v");
            assertTrue(wrapper.getId().startsWith(NodeNamePrefix.API_COMPLETION_WRAPPER),
                    "节点 ID 应以 '" + NodeNamePrefix.API_COMPLETION_WRAPPER + "' 开头，实际: " + wrapper.getId());
        }

        @Test
        @DisplayName("每个实例的节点 ID 应唯一")
        void nodeId_isUnique() {
            ApiCompletionWrapper w1 = buildWrapper("k", "v");
            ApiCompletionWrapper w2 = buildWrapper("k", "v");
            assertNotEquals(w1.getId(), w2.getId());
        }
    }

    // ==================== doExecute 保护 ====================

    @Nested
    @DisplayName("doExecute 空数据保护")
    class DoExecuteGuardTest {

        @Test
        @DisplayName("dataItems 为 null 时应抛出 EvalException")
        void doExecute_nullDataItems_throwsEvalException() {
            ApiCompletionWrapper wrapper = buildWrapper("k", "v");
            WorkflowContext ctx = new WorkflowContext();
            WorkflowContextOps.setDataItems(ctx, null); // null → remove key → getDataItems 返回 null
            wrapper.setWorkflowContext(ctx);
            assertThrows(EvalException.class, wrapper::doExecute);
        }

        @Test
        @DisplayName("dataItems 为空列表时应抛出 EvalException")
        void doExecute_emptyDataItems_throwsEvalException() {
            ApiCompletionWrapper wrapper = buildWrapper("k", "v");
            assertThrows(EvalException.class,
                    () -> executeWithContext(wrapper, new ArrayList<>()));
        }

        @Test
        @DisplayName("dataItems 非空时正常执行，不抛异常")
        void doExecute_normalDataItems_noException() {
            ApiCompletionWrapper wrapper = buildWrapper("transformed", "yes");
            List<DataItem> items = ListUtils.of(buildDataItem(1L));
            assertDoesNotThrow(() -> executeWithContext(wrapper, items));
        }
    }

    // ==================== 钩子顺序 ====================

    @Nested
    @DisplayName("钩子调用顺序")
    class HookOrderTest {

        @Test
        @DisplayName("正常执行时钩子顺序为 before → wrapper → after")
        void executeWrapper_hookOrder_beforeWrapperAfter() {
            List<String> callLog = new ArrayList<>();
            ApiCompletionWrapper wrapper = buildHookOrderWrapper(callLog);
            DataItem item = buildDataItem(1L);

            wrapper.executeWrapper(item);

            assertEquals(ListUtils.of("before", "wrapper", "after"), callLog);
        }

        @Test
        @DisplayName("wrapper 抛异常时 after 不执行，after 前已记录 before")
        void executeWrapper_exceptionInWrapper_afterNotCalled() {
            List<String> callLog = new ArrayList<>();
            ApiCompletionWrapper wrapper = new ApiCompletionWrapper() {
                @Override
                protected void beforeWrapper(DataItem dataItem) {
                    callLog.add("before");
                }

                @Override
                protected void wrapper(DataItem dataItem) {
                    callLog.add("wrapper-throws");
                    throw new RuntimeException("异常");
                }

                @Override
                protected void afterWrapper(DataItem dataItem) {
                    callLog.add("after");
                }
            };

            wrapper.executeWrapper(buildDataItem(1L));

            assertTrue(callLog.contains("before"));
            assertTrue(callLog.contains("wrapper-throws"));
            assertFalse(callLog.contains("after"), "after 不应在 wrapper 抛异常后执行");
        }
    }

    // ==================== 异常隔离 ====================

    @Nested
    @DisplayName("单条异常不影响整体")
    class ExceptionIsolationTest {

        @Test
        @DisplayName("wrapper 抛异常时 executeWrapper 返回原 DataItem（不为 null）")
        void executeWrapper_exceptionInWrapper_returnsOriginalItem() {
            RuntimeException ex = new RuntimeException("装饰失败");
            ApiCompletionWrapper wrapper = buildThrowingWrapper(ex);
            DataItem item = buildDataItem(1L);

            DataItem returned = wrapper.executeWrapper(item);

            assertSame(item, returned, "应原样返回 DataItem");
        }

        @Test
        @DisplayName("onWrapperError 在 wrapper 抛异常时被调用，且携带正确异常")
        void executeWrapper_exceptionInWrapper_onWrapperErrorCalled() {
            List<Throwable> errors = new ArrayList<>();
            ApiCompletionWrapper wrapper = buildErrorCapturingWrapper(errors);
            wrapper.executeWrapper(buildDataItem(1L));

            assertEquals(1, errors.size());
            assertEquals("故意抛出", errors.get(0).getMessage());
        }

        @Test
        @DisplayName("多条数据项中，部分失败不影响其他条的装饰结果")
        void doExecute_partialFailure_otherItemsStillWrapped() {
            // 奇数 index 的 DataItem 触发异常，偶数的正常写入
            ApiCompletionWrapper wrapper = new ApiCompletionWrapper() {
                @Override
                protected void wrapper(DataItem dataItem) {
                    if (dataItem.getDataIndex() % 2 != 0) {
                        throw new RuntimeException("奇数行故意失败");
                    }
                    dataItem.getApiCompletionResult().set("wrapped", "true");
                }
            };

            List<DataItem> items = ListUtils.of(
                    buildDataItem(1L),  // 失败
                    buildDataItem(2L),  // 成功
                    buildDataItem(3L),  // 失败
                    buildDataItem(4L)   // 成功
            );

            assertDoesNotThrow(() -> executeWithContext(wrapper, items));

            // 偶数条应成功写入
            assertEquals("true", items.get(1).getApiCompletionResult().get("wrapped"));
            assertEquals("true", items.get(3).getApiCompletionResult().get("wrapped"));
            // 奇数条结果不变
            assertNull(items.get(0).getApiCompletionResult().get("wrapped"));
            assertNull(items.get(2).getApiCompletionResult().get("wrapped"));
        }
    }

    // ==================== 装饰结果回写 ====================

    @Nested
    @DisplayName("装饰结果回写")
    class WrapperResultTest {

        @Test
        @DisplayName("wrapper 对 ApiCompletionResult 的修改应正确回写到 DataItem")
        void wrapper_modifiesApiCompletionResult_changesPersist() {
            ApiCompletionWrapper wrapper = buildWrapper("normalized_answer", "hello world");
            DataItem item = buildDataItem(1L);

            wrapper.executeWrapper(item);

            assertEquals("hello world", item.getApiCompletionResult().<String>get("normalized_answer"));
        }

        @Test
        @DisplayName("多个字段同时写入，均应正确保留")
        void wrapper_multipleFieldsWritten_allPersist() {
            ApiCompletionWrapper wrapper = new ApiCompletionWrapper() {
                @Override
                protected void wrapper(DataItem dataItem) {
                    ApiCompletionResult result = dataItem.getApiCompletionResult();
                    result.set("field_a", "valueA");
                    result.set("field_b", 42);
                    result.set("field_c", true);
                }
            };
            DataItem item = buildDataItem(1L);
            wrapper.executeWrapper(item);

            ApiCompletionResult result = item.getApiCompletionResult();
            assertEquals("valueA", result.get("field_a"));
            assertEquals(42, (Integer) result.get("field_b"));
            assertEquals(true, result.get("field_c"));
        }

        @Test
        @DisplayName("doExecute 批量执行后，所有 DataItem 均被正确装饰")
        void doExecute_batchWrapper_allItemsDecorated() {
            ApiCompletionWrapper wrapper = buildWrapper("done", "yes");
            List<DataItem> items = ListUtils.of(
                    buildDataItem(1L), buildDataItem(2L), buildDataItem(3L)
            );

            executeWithContext(wrapper, items);

            for (DataItem item : items) {
                assertEquals("yes", item.getApiCompletionResult().<String>get("done"),
                        "DataItem[" + item.getDataIndex() + "] 未被正确装饰");
            }
        }
    }

    // ==================== 自定义 Config ====================

    @Nested
    @DisplayName("Config 生效")
    class ConfigTest {

        @Test
        @DisplayName("默认构造器使用 threadNum=1")
        void defaultConstructor_threadNumIsOne() {
            ApiCompletionWrapper wrapper = buildWrapper("k", "v");
            assertEquals(1, wrapper.config.getThreadNum());
        }

        @Test
        @DisplayName("自定义 config 的 threadNum 正确生效")
        void customConfig_threadNumApplied() {
            ApiCompletionWrapperConfig config = ApiCompletionWrapperConfig.builder()
                    .threadNum(4)
                    .build();
            ApiCompletionWrapper wrapper = new ApiCompletionWrapper(config) {
                @Override
                protected void wrapper(DataItem dataItem) {
                }
            };
            assertEquals(4, wrapper.config.getThreadNum());
        }
    }
}