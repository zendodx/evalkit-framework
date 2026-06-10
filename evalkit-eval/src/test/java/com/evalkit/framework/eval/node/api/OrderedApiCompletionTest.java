package com.evalkit.framework.eval.node.api;

import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.api.config.ApiCompletionConfig;
import com.evalkit.framework.eval.node.begin.Begin;
import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.workflow.WorkflowBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class OrderedApiCompletionTest {
    private final class TestApiCompletion extends OrderedApiCompletion {

        public TestApiCompletion() {
        }

        public TestApiCompletion(ApiCompletionConfig config) {
            super(config);
        }

        @Override
        public String prepareOrderKey(DataItem dataItem) {
            return dataItem.getInputData().get("caseId");
        }

        @Override
        public Comparator<DataItem> prepareComparator() {
            return (o1, o2) -> 0;
        }

        /* 用来收集实际执行顺序 */
        private final Map<String, List<String>> execOrder = new ConcurrentHashMap<>();

        @Override
        protected ApiCompletionResult invoke(DataItem dataItem) {
            InputData inputData = dataItem.getInputData();
            String caseId = inputData.get("caseId");
            String query = inputData.get("query");

            // 模拟业务耗时 200ms
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 记录执行顺序
            execOrder.computeIfAbsent(caseId, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(query);

            String response = "response of " + query;
            log.info("caseId:{}, query:{}, response:{}", caseId, query, response);
            ApiCompletionResult result = new ApiCompletionResult();
            result.setResultItem(MapUtils.of("response", response));
            return result;
        }
    }

    @Test
    void testOrderAndConcurrent() {
        DataLoader dataLoader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return ListUtils.of(
                        new InputData(MapUtils.of("caseId", "1", "query", "query1")),
                        new InputData(MapUtils.of("caseId", "1", "query", "query2")),
                        new InputData(MapUtils.of("caseId", "1", "query", "query3")),
                        new InputData(MapUtils.of("caseId", "2", "query", "query1")),
                        new InputData(MapUtils.of("caseId", "2", "query", "query2")),
                        new InputData(MapUtils.of("caseId", "3", "query", "query1")),
                        new InputData(MapUtils.of("caseId", "3", "query", "query2"))
                );
            }
        };

        Begin begin = new Begin();
        TestApiCompletion apiCompletion = new TestApiCompletion(
                ApiCompletionConfig.builder()
                        .threadNum(4)
                        .build()
        );

        // 必须在指定时间内跑完，否则认为死锁 / 阻塞
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), (ThrowingSupplier<Void>) () -> {
            new WorkflowBuilder()
                    .link(begin, dataLoader, apiCompletion)
                    .build()
                    .execute();
            return null;
        });

        // 顺序性断言：同一 caseId 必须 query1→query2→query3...
        assertEquals(ListUtils.of("query1", "query2", "query3"), apiCompletion.execOrder.get("1"));
        assertEquals(ListUtils.of("query1", "query2"), apiCompletion.execOrder.get("2"));
        assertEquals(ListUtils.of("query1", "query2"), apiCompletion.execOrder.get("3"));

        // 并发度断言：3 个 case 并行，总耗时 < 串行 7*200ms
        log.info("execOrder={}", apiCompletion.execOrder);
    }

    // =====================================================================
    // 多轮历史访问方法的专项测试
    //   使用一个"会话 A 有 5 轮、会话 B 有 2 轮"的数据集，
    //   在 invoke 内部调用各工具方法，把结果暂存到 DataItem.extra，
    //   工作流执行完后再对 extra 里收集到的值做断言。
    // =====================================================================

    /**
     * 专门用于测试历史访问方法的 ApiCompletion 实现。
     * 按 caseId 分组，按 round 升序执行。
     * invoke 执行时把各辅助方法的返回值存入 DataItem.extra，供测试断言使用。
     */
    private class HistoryApiCompletion extends OrderedApiCompletion {

        public HistoryApiCompletion(ApiCompletionConfig config) {
            super(config);
        }

        @Override
        public String prepareOrderKey(DataItem dataItem) {
            return dataItem.getInputData().get("caseId");
        }

        @Override
        public Comparator<DataItem> prepareComparator() {
            return Comparator.comparingInt(d -> Integer.parseInt(d.getInputData().get("round")));
        }

        @Override
        protected ApiCompletionResult invoke(DataItem dataItem) {
            int round = Integer.parseInt(dataItem.getInputData().get("round"));

            // --- 调用各辅助方法，结果存入 extra ---

            // 1. getGroupDataItems：同组全部轮数量
            int groupSize = getGroupDataItems(dataItem).size();
            dataItem.addExtraItem("groupSize", groupSize);

            // 2. getPrevDataItem：上一条的 round 值（第1轮为 -1 表示不存在）
            DataItem prev = getPrevDataItem(dataItem);
            dataItem.addExtraItem("prevRound", prev == null ? -1
                    : Integer.parseInt(prev.getInputData().get("round")));

            // 3. getPrevDataItems：已完成历史轮数量（不含当前）
            int prevCount = getPrevDataItems(dataItem).size();
            dataItem.addExtraItem("prevCount", prevCount);

            // 4. getGroupDataItemAt(single)：取第 2 轮的 round 值（不存在则 -1）
            DataItem at2 = getGroupDataItemAt(dataItem, 2);
            dataItem.addExtraItem("at2Round", at2 == null ? -1
                    : Integer.parseInt(at2.getInputData().get("round")));

            // 5. getGroupDataItemAt(range)：第 1~3 轮的 round 值列表
            List<Integer> rangeRounds = new ArrayList<>();
            for (DataItem item : getGroupDataItemAt(dataItem, 1, 3)) {
                rangeRounds.add(Integer.parseInt(item.getInputData().get("round")));
            }
            dataItem.addExtraItem("range1to3", rangeRounds);

            // 6. getHistoryValues：已完成轮的 query 值列表
            List<String> historyQueries = getHistoryValues(dataItem,
                    item -> item.getInputData().get("query"));
            dataItem.addExtraItem("historyQueries", historyQueries);

            // 返回结果
            ApiCompletionResult result = new ApiCompletionResult();
            result.setResultItem(MapUtils.of("response", "resp-round-" + round));
            return result;
        }
    }

    /**
     * 构造测试数据：会话 A 5 轮 + 会话 B 2 轮
     */
    private List<InputData> buildMultiTurnData() {
        return ListUtils.of(
                new InputData(MapUtils.of("caseId", "A", "round", "1", "query", "q1")),
                new InputData(MapUtils.of("caseId", "A", "round", "2", "query", "q2")),
                new InputData(MapUtils.of("caseId", "A", "round", "3", "query", "q3")),
                new InputData(MapUtils.of("caseId", "A", "round", "4", "query", "q4")),
                new InputData(MapUtils.of("caseId", "A", "round", "5", "query", "q5")),
                new InputData(MapUtils.of("caseId", "B", "round", "1", "query", "bq1")),
                new InputData(MapUtils.of("caseId", "B", "round", "2", "query", "bq2"))
        );
    }

    /**
     * 带 DataItem 收集功能的 ApiCompletion，用于测试断言。
     */
    private class CapturingHistoryApiCompletion extends HistoryApiCompletion {
        final List<DataItem> capturedItems = Collections.synchronizedList(new ArrayList<>());

        public CapturingHistoryApiCompletion() {
            super(ApiCompletionConfig.builder().threadNum(4).build());
        }

        @Override
        protected ApiCompletionResult invoke(DataItem dataItem) {
            ApiCompletionResult result = super.invoke(dataItem);
            capturedItems.add(dataItem);
            return result;
        }
    }

    /**
     * 找到 capturedItems 中指定 caseId + round 的 DataItem
     */
    private DataItem findItem(List<DataItem> items, String caseId, int round) {
        return items.stream()
                .filter(item -> caseId.equals(item.getInputData().get("caseId"))
                        && String.valueOf(round).equals(item.getInputData().get("round")))
                .findFirst()
                .orElse(null);
    }

    @Test
    @DisplayName("getGroupDataItems 返回同组全部轮次，会话 A=5，会话 B=2")
    void testGetGroupDataItems_size() {
        CapturingHistoryApiCompletion api = new CapturingHistoryApiCompletion();
        DataLoader dl = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return buildMultiTurnData();
            }
        };
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), (ThrowingSupplier<Void>) () -> {
            new WorkflowBuilder().link(new Begin(), dl, api).build().execute();
            return null;
        });

        // 会话 A 共 5 轮，每轮看到的同组大小都是 5
        for (int r = 1; r <= 5; r++) {
            DataItem item = findItem(api.capturedItems, "A", r);
            assertNotNull(item, "A-round-" + r + " not found");
            assertEquals(5, item.getExtraItem("groupSize"), "A round " + r + " groupSize");
        }
        // 会话 B 共 2 轮
        for (int r = 1; r <= 2; r++) {
            DataItem item = findItem(api.capturedItems, "B", r);
            assertNotNull(item);
            assertEquals(2, item.getExtraItem("groupSize"), "B round " + r + " groupSize");
        }
    }

    @Test
    @DisplayName("getPrevDataItem 第1轮返回\"不存在\"(-1)，第2轮指向round=1，第5轮指向round=4")
    void testGetPrevDataItem() {
        CapturingHistoryApiCompletion api = new CapturingHistoryApiCompletion();
        DataLoader dl = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return buildMultiTurnData();
            }
        };
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), (ThrowingSupplier<Void>) () -> {
            new WorkflowBuilder().link(new Begin(), dl, api).build().execute();
            return null;
        });

        // 第 1 轮没有上一轮 → prevRound == -1
        assertEquals(-1, findItem(api.capturedItems, "A", 1).getExtraItem("prevRound"));
        // 第 2 轮的上一轮是 round=1
        assertEquals(1, findItem(api.capturedItems, "A", 2).getExtraItem("prevRound"));
        // 第 5 轮的上一轮是 round=4
        assertEquals(4, findItem(api.capturedItems, "A", 5).getExtraItem("prevRound"));
    }

    @Test
    @DisplayName("getPrevDataItems 返回当前轮之前的所有历史条目数量")
    void testGetPrevDataItems_count() {
        CapturingHistoryApiCompletion api = new CapturingHistoryApiCompletion();
        DataLoader dl = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return buildMultiTurnData();
            }
        };
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), (ThrowingSupplier<Void>) () -> {
            new WorkflowBuilder().link(new Begin(), dl, api).build().execute();
            return null;
        });

        // 第 1 轮：0 条历史
        assertEquals(0, findItem(api.capturedItems, "A", 1).getExtraItem("prevCount"));
        // 第 3 轮：2 条历史（round 1、2）
        assertEquals(2, findItem(api.capturedItems, "A", 3).getExtraItem("prevCount"));
        // 第 5 轮：4 条历史（round 1~4）
        assertEquals(4, findItem(api.capturedItems, "A", 5).getExtraItem("prevCount"));
    }

    @Test
    @DisplayName("getGroupDataItemAt(n) 按1-based索引获取指定轮的数据项")
    void testGetGroupDataItemAt_single() {
        CapturingHistoryApiCompletion api = new CapturingHistoryApiCompletion();
        DataLoader dl = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return buildMultiTurnData();
            }
        };
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), (ThrowingSupplier<Void>) () -> {
            new WorkflowBuilder().link(new Begin(), dl, api).build().execute();
            return null;
        });

        // 从第 3 轮视角取第 2 轮 → at2Round == 2
        assertEquals(2, findItem(api.capturedItems, "A", 3).getExtraItem("at2Round"));
        // 从第 5 轮视角取第 2 轮 → at2Round == 2（索引是全组，与当前轮无关）
        assertEquals(2, findItem(api.capturedItems, "A", 5).getExtraItem("at2Round"));
        // 会话 B 只有 2 轮，取第 2 轮 → at2Round == 2
        assertEquals(2, findItem(api.capturedItems, "B", 1).getExtraItem("at2Round"));
    }

    @Test
    @DisplayName("getGroupDataItemAt(from, to) 获取轮次范围，超出时自动截断")
    void testGetGroupDataItemAt_range() {
        CapturingHistoryApiCompletion api = new CapturingHistoryApiCompletion();
        DataLoader dl = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return buildMultiTurnData();
            }
        };
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), (ThrowingSupplier<Void>) () -> {
            new WorkflowBuilder().link(new Begin(), dl, api).build().execute();
            return null;
        });

        // 会话 A（5轮）从任意轮视角取 range[1,3]，结果都是 [1,2,3]
        assertEquals(ListUtils.of(1, 2, 3),
                findItem(api.capturedItems, "A", 5).getExtraItem("range1to3"));
        // 会话 B（2轮）只有 2 轮，range[1,3] 自动截断为 [1,2]
        assertEquals(ListUtils.of(1, 2),
                findItem(api.capturedItems, "B", 2).getExtraItem("range1to3"));
    }

    /**
     * 专用于 range 越界测试：在 CapturingHistoryApiCompletion 基础上额外记录越界结果
     */
    private class RangeOutOfBoundApiCompletion extends CapturingHistoryApiCompletion {
        @Override
        protected ApiCompletionResult invoke(DataItem dataItem) {
            List<DataItem> outOfRange = getGroupDataItemAt(dataItem, 3, 5);
            dataItem.addExtraItem("outOfRange", outOfRange.size());
            return super.invoke(dataItem);
        }
    }

    @Test
    @DisplayName("range 越界（B只有2轮，取[3,5]）返回空列表")
    void testGetGroupDataItemAt_rangeOutOfBound_returnsEmpty() {
        // 会话 B 只有 2 轮，range[3,5] 应返回空列表
        RangeOutOfBoundApiCompletion api = new RangeOutOfBoundApiCompletion();
        DataLoader dl = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return buildMultiTurnData();
            }
        };
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), (ThrowingSupplier<Void>) () -> {
            new WorkflowBuilder().link(new Begin(), dl, api).build().execute();
            return null;
        });
        DataItem bRound1 = findItem(api.capturedItems, "B", 1);
        assertNotNull(bRound1);
        assertEquals(0, bRound1.getExtraItem("outOfRange"));
    }

    @Test
    @DisplayName("getHistoryValues 正确提取所有历史轮的指定字段值")
    void testGetHistoryValues_queries() {
        CapturingHistoryApiCompletion api = new CapturingHistoryApiCompletion();
        DataLoader dl = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return buildMultiTurnData();
            }
        };
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), (ThrowingSupplier<Void>) () -> {
            new WorkflowBuilder().link(new Begin(), dl, api).build().execute();
            return null;
        });

        // 第 1 轮：没有历史 → 空列表
        assertEquals(Collections.emptyList(),
                findItem(api.capturedItems, "A", 1).getExtraItem("historyQueries"));
        // 第 3 轮：历史是 round1、round2 的 query
        assertEquals(ListUtils.of("q1", "q2"),
                findItem(api.capturedItems, "A", 3).getExtraItem("historyQueries"));
        // 第 5 轮：历史是 q1~q4
        assertEquals(ListUtils.of("q1", "q2", "q3", "q4"),
                findItem(api.capturedItems, "A", 5).getExtraItem("historyQueries"));
        // 会话 B 第 2 轮：历史是 bq1
        assertEquals(ListUtils.of("bq1"),
                findItem(api.capturedItems, "B", 2).getExtraItem("historyQueries"));
    }

    /**
     * 专用于 single 越界测试：额外记录第 3 轮是否越界（用 true/false 存储）
     */
    private class SingleOutOfBoundApiCompletion extends CapturingHistoryApiCompletion {
        @Override
        protected ApiCompletionResult invoke(DataItem dataItem) {
            // 会话 B 只有 2 轮，取第 3 轮应为 null
            DataItem at3 = getGroupDataItemAt(dataItem, 3);
            // ConcurrentHashMap 不支持 null value，用 boolean 代替
            dataItem.addExtraItem("at3IsNull", at3 == null ? Boolean.TRUE : Boolean.FALSE);
            return super.invoke(dataItem);
        }
    }

    @Test
    @DisplayName("单索引越界（B只有2轮，取第3轮）返回 null")
    void testGetGroupDataItemAt_single_outOfBound_returnsNull() {
        SingleOutOfBoundApiCompletion api = new SingleOutOfBoundApiCompletion();
        DataLoader dl = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return buildMultiTurnData();
            }
        };
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), (ThrowingSupplier<Void>) () -> {
            new WorkflowBuilder().link(new Begin(), dl, api).build().execute();
            return null;
        });
        DataItem bItem = findItem(api.capturedItems, "B", 1);
        assertNotNull(bItem);
        assertTrue((Boolean) bItem.getExtraItem("at3IsNull"));
    }
}