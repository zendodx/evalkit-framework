package com.evalkit.framework.eval.node.api_wrapper;

import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.api_wrapper.config.LLMBasedApiCompletionConfig;
import com.evalkit.framework.infra.service.llm.LLMService;
import com.evalkit.framework.workflow.model.WorkflowContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLMBasedApiCompletionWrapper 单元测试
 * <p>
 * 测试覆盖：
 * <ul>
 *   <li>llmConfig 正确绑定，无字段遮蔽</li>
 *   <li>ApiCompletionResult 为 null 时跳过，不调用 LLM</li>
 *   <li>preparePrompt 返回空/null 时跳过，不调用 LLM</li>
 *   <li>正常流程：LLM 调用结果通过 applyLLMOutput 写回结果</li>
 *   <li>preparePrompt 接收到完整的 DataItem（含输入数据和接口结果）</li>
 *   <li>LLM 抛异常时，executeWrapper 不向外传播（单条失败隔离）</li>
 *   <li>多条数据批量执行，LLM 分别独立调用</li>
 * </ul>
 */
@DisplayName("LLMBasedApiCompletionWrapper")
class LLMBasedApiCompletionWrapperTest {

    // ==================== 工厂方法 ====================

    /**
     * 构造固定回复的 mock LLMService
     */
    private LLMService mockLLM(String reply) {
        return new LLMService() {
            @Override
            public String chat(String prompt) {
                return reply;
            }

            @Override
            public String getModel() {
                return "mock-model";
            }
        };
    }

    /**
     * 构造抛异常的 mock LLMService
     */
    private LLMService throwingLLM(String msg) {
        return new LLMService() {
            @Override
            public String chat(String prompt) {
                throw new RuntimeException(msg);
            }

            @Override
            public String getModel() {
                return "throwing-model";
            }
        };
    }

    /**
     * 构造记录调用 prompt 的 mock LLMService
     */
    private LLMService capturingLLM(List<String> promptLog, String reply) {
        return new LLMService() {
            @Override
            public String chat(String prompt) {
                promptLog.add(prompt);
                return reply;
            }

            @Override
            public String getModel() {
                return "capturing-model";
            }
        };
    }

    /**
     * 构造标准 LLMBasedApiCompletionWrapper：
     * - preparePrompt: 拼接 query + answer
     * - applyLLMOutput: 写入 "wrapped_answer" 字段
     */
    private LLMBasedApiCompletionWrapper buildWrapper(LLMService llmService) {
        LLMBasedApiCompletionConfig config = LLMBasedApiCompletionConfig.builder()
                .llmService(llmService)
                .build();
        return new LLMBasedApiCompletionWrapper(config) {
            @Override
            public String preparePrompt(DataItem dataItem) {
                String query = dataItem.getInputData().get("query");
                String answer = dataItem.getApiCompletionResult().get("answer");
                return "query=" + query + " answer=" + answer;
            }

            @Override
            public void applyLLMOutput(ApiCompletionResult result, String llmOutput) {
                result.set("wrapped_answer", llmOutput);
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
    private void executeWithContext(LLMBasedApiCompletionWrapper wrapper, List<DataItem> items) {
        WorkflowContext ctx = new WorkflowContext();
        WorkflowContextOps.setDataItems(ctx, items);
        wrapper.setWorkflowContext(ctx);
        wrapper.doExecute();
    }

    // ==================== Config 绑定 ====================

    @Nested
    @DisplayName("Config 绑定")
    class ConfigBindingTest {

        @Test
        @DisplayName("llmConfig 字段与构造器传入的 config 是同一实例，不存在字段遮蔽")
        void llmConfig_sameInstanceAsConstructorArg() {
            LLMBasedApiCompletionConfig config = LLMBasedApiCompletionConfig.builder()
                    .llmService(mockLLM("ok"))
                    .build();
            LLMBasedApiCompletionWrapper wrapper = new LLMBasedApiCompletionWrapper(config) {
                @Override
                public String preparePrompt(DataItem dataItem) {
                    return "prompt";
                }

                @Override
                public void applyLLMOutput(ApiCompletionResult result, String llmOutput) {
                }
            };
            assertSame(config, wrapper.llmConfig, "llmConfig 应与构造器传入的 config 是同一对象");
        }

        @Test
        @DisplayName("父类 config 与 llmConfig 指向同一实例")
        void parentConfig_sameAsLlmConfig() {
            LLMBasedApiCompletionConfig config = LLMBasedApiCompletionConfig.builder()
                    .llmService(mockLLM("ok"))
                    .build();
            LLMBasedApiCompletionWrapper wrapper = new LLMBasedApiCompletionWrapper(config) {
                @Override
                public String preparePrompt(DataItem dataItem) {
                    return "prompt";
                }

                @Override
                public void applyLLMOutput(ApiCompletionResult result, String llmOutput) {
                }
            };
            // 父类 config 也应与 llmConfig 一致
            assertSame(wrapper.llmConfig, wrapper.config,
                    "父类 config 与 llmConfig 应为同一实例");
        }
    }

    // ==================== 跳过条件 ====================

    @Nested
    @DisplayName("跳过条件")
    class SkipConditionTest {

        @Test
        @DisplayName("ApiCompletionResult 为 null 时，不调用 LLM，直接跳过")
        void wrapper_nullApiCompletionResult_skipsLLM() {
            AtomicInteger callCount = new AtomicInteger(0);
            LLMService countingLLM = new LLMService() {
                @Override
                public String chat(String prompt) {
                    callCount.incrementAndGet();
                    return "output";
                }

                @Override
                public String getModel() {
                    return "counting-model";
                }
            };
            LLMBasedApiCompletionWrapper wrapper = buildWrapper(countingLLM);

            DataItem item = buildDataItem(1L);
            item.setApiCompletionResult(null);  // 设为 null

            wrapper.executeWrapper(item);

            assertEquals(0, callCount.get(), "ApiCompletionResult 为 null 时不应调用 LLM");
        }

        @Test
        @DisplayName("preparePrompt 返回 null 时，不调用 LLM，直接跳过")
        void wrapper_nullPrompt_skipsLLM() {
            AtomicInteger callCount = new AtomicInteger(0);
            LLMBasedApiCompletionConfig config = LLMBasedApiCompletionConfig.builder()
                    .llmService(new LLMService() {
                        @Override
                        public String chat(String prompt) {
                            callCount.incrementAndGet();
                            return "output";
                        }

                        @Override
                        public String getModel() {
                            return "counting-model";
                        }
                    })
                    .build();
            LLMBasedApiCompletionWrapper wrapper = new LLMBasedApiCompletionWrapper(config) {
                @Override
                public String preparePrompt(DataItem dataItem) {
                    return null;  // 返回 null
                }

                @Override
                public void applyLLMOutput(ApiCompletionResult result, String llmOutput) {
                }
            };

            wrapper.executeWrapper(buildDataItem(1L));

            assertEquals(0, callCount.get(), "preparePrompt 返回 null 时不应调用 LLM");
        }

        @Test
        @DisplayName("preparePrompt 返回空字符串时，不调用 LLM，直接跳过")
        void wrapper_emptyPrompt_skipsLLM() {
            AtomicInteger callCount = new AtomicInteger(0);
            LLMBasedApiCompletionConfig config = LLMBasedApiCompletionConfig.builder()
                    .llmService(new LLMService() {
                        @Override
                        public String chat(String prompt) {
                            callCount.incrementAndGet();
                            return "output";
                        }

                        @Override
                        public String getModel() {
                            return "counting-model";
                        }
                    })
                    .build();
            LLMBasedApiCompletionWrapper wrapper = new LLMBasedApiCompletionWrapper(config) {
                @Override
                public String preparePrompt(DataItem dataItem) {
                    return "";  // 返回空字符串
                }

                @Override
                public void applyLLMOutput(ApiCompletionResult result, String llmOutput) {
                }
            };

            wrapper.executeWrapper(buildDataItem(1L));

            assertEquals(0, callCount.get(), "preparePrompt 返回空字符串时不应调用 LLM");
        }
    }

    // ==================== 正常流程 ====================

    @Nested
    @DisplayName("正常装饰流程")
    class NormalFlowTest {

        @Test
        @DisplayName("LLM 输出通过 applyLLMOutput 正确写回 ApiCompletionResult")
        void wrapper_llmOutput_appliedToResult() {
            LLMBasedApiCompletionWrapper wrapper = buildWrapper(mockLLM("转化后的内容"));
            DataItem item = buildDataItem(1L);

            wrapper.executeWrapper(item);

            assertEquals("转化后的内容", item.getApiCompletionResult().<String>get("wrapped_answer"));
        }

        @Test
        @DisplayName("preparePrompt 接收到正确的 DataItem（含 inputData 和 apiCompletionResult）")
        void wrapper_preparePrompt_receivesCorrectDataItem() {
            AtomicReference<String> capturedPrompt = new AtomicReference<>();
            LLMBasedApiCompletionConfig config = LLMBasedApiCompletionConfig.builder()
                    .llmService(mockLLM("output"))
                    .build();
            LLMBasedApiCompletionWrapper wrapper = new LLMBasedApiCompletionWrapper(config) {
                @Override
                public String preparePrompt(DataItem dataItem) {
                    String q = dataItem.getInputData().get("query");
                    String a = dataItem.getApiCompletionResult().get("answer");
                    capturedPrompt.set("q=" + q + ",a=" + a);
                    return capturedPrompt.get();
                }

                @Override
                public void applyLLMOutput(ApiCompletionResult result, String llmOutput) {
                }
            };

            DataItem item = buildDataItem(42L);
            wrapper.executeWrapper(item);

            assertEquals("q=测试问题-42,a=测试回答-42", capturedPrompt.get());
        }

        @Test
        @DisplayName("LLM 被调用时收到的 prompt 与 preparePrompt 返回值一致")
        void wrapper_llmReceivesCorrectPrompt() {
            List<String> promptLog = new ArrayList<>();
            LLMBasedApiCompletionConfig config = LLMBasedApiCompletionConfig.builder()
                    .llmService(capturingLLM(promptLog, "output"))
                    .build();
            LLMBasedApiCompletionWrapper wrapper = new LLMBasedApiCompletionWrapper(config) {
                @Override
                public String preparePrompt(DataItem dataItem) {
                    return "固定提示词";
                }

                @Override
                public void applyLLMOutput(ApiCompletionResult result, String llmOutput) {
                }
            };

            wrapper.executeWrapper(buildDataItem(1L));

            assertEquals(1, promptLog.size());
            assertEquals("固定提示词", promptLog.get(0));
        }
    }

    // ==================== 异常隔离 ====================

    @Nested
    @DisplayName("LLM 异常隔离")
    class LLMExceptionIsolationTest {

        @Test
        @DisplayName("LLM 抛异常时，executeWrapper 不向外传播，返回原 DataItem")
        void wrapper_llmThrows_exceptionIsolated() {
            LLMBasedApiCompletionWrapper wrapper = buildWrapper(throwingLLM("LLM 服务故障"));
            DataItem item = buildDataItem(1L);

            DataItem returned = assertDoesNotThrow(() -> wrapper.executeWrapper(item));

            assertSame(item, returned, "应原样返回 DataItem");
        }

        @Test
        @DisplayName("多条数据中，部分 LLM 异常不影响其他条")
        void doExecute_partialLLMFailure_otherItemsStillWrapped() {
            AtomicInteger callCount = new AtomicInteger(0);
            LLMBasedApiCompletionConfig config = LLMBasedApiCompletionConfig.builder()
                    .llmService(new LLMService() {
                        @Override
                        public String chat(String prompt) {
                            // 第一次调用失败，后续正常
                            if (callCount.getAndIncrement() == 0) {
                                throw new RuntimeException("第一次失败");
                            }
                            return "success";
                        }

                        @Override
                        public String getModel() {
                            return "partial-fail-model";
                        }
                    })
                    .build();
            LLMBasedApiCompletionWrapper wrapper = new LLMBasedApiCompletionWrapper(config) {
                @Override
                public String preparePrompt(DataItem dataItem) {
                    return "prompt";
                }

                @Override
                public void applyLLMOutput(ApiCompletionResult result, String llmOutput) {
                    result.set("wrapped", llmOutput);
                }
            };

            List<DataItem> items = ListUtils.of(buildDataItem(1L), buildDataItem(2L), buildDataItem(3L));
            assertDoesNotThrow(() -> executeWithContext(wrapper, items));

            // 后两条应成功
            int successCount = 0;
            for (DataItem item : items) {
                if ("success".equals(item.getApiCompletionResult().<String>get("wrapped"))) {
                    successCount++;
                }
            }
            assertEquals(2, successCount, "第一条失败后，后续两条应正常装饰");
        }
    }

    // ==================== 批量执行 ====================

    @Nested
    @DisplayName("批量执行")
    class BatchExecutionTest {

        @Test
        @DisplayName("doExecute 对所有 DataItem 各调用一次 LLM")
        void doExecute_callsLLMForEachItem() {
            List<String> promptLog = new ArrayList<>();
            LLMBasedApiCompletionConfig config = LLMBasedApiCompletionConfig.builder()
                    .llmService(capturingLLM(promptLog, "output"))
                    .build();
            LLMBasedApiCompletionWrapper wrapper = new LLMBasedApiCompletionWrapper(config) {
                @Override
                public String preparePrompt(DataItem dataItem) {
                    return "prompt-" + dataItem.getDataIndex();
                }

                @Override
                public void applyLLMOutput(ApiCompletionResult result, String llmOutput) {
                    result.set("out", llmOutput);
                }
            };

            List<DataItem> items = ListUtils.of(buildDataItem(1L), buildDataItem(2L), buildDataItem(3L));
            executeWithContext(wrapper, items);

            assertEquals(3, promptLog.size(), "应为每条数据各调用一次 LLM");
        }

        @Test
        @DisplayName("doExecute 完成后，所有 DataItem 的结果均被正确写入")
        void doExecute_allItemsDecorated() {
            LLMBasedApiCompletionWrapper wrapper = buildWrapper(mockLLM("processed"));
            List<DataItem> items = ListUtils.of(
                    buildDataItem(1L), buildDataItem(2L), buildDataItem(3L)
            );

            executeWithContext(wrapper, items);

            for (DataItem item : items) {
                assertEquals("processed", item.getApiCompletionResult().<String>get("wrapped_answer"),
                        "DataItem[" + item.getDataIndex() + "] 装饰结果不正确");
            }
        }
    }
}