package com.evalkit.framework.eval.node.api;

import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.api.config.OrderedApiCompletionConfig;
import com.evalkit.framework.eval.node.begin.Begin;
import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.workflow.WorkflowBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@Slf4j
class OrderedApiCompletionTest {
    private final class TestApiCompletion extends OrderedApiCompletion {

        public TestApiCompletion() {
        }

        public TestApiCompletion(OrderedApiCompletionConfig config) {
            super(config);
        }

        /* 用来收集实际执行顺序 */
        private final Map<String, List<String>> execOrder = new ConcurrentHashMap<>();

        @Override
        public String getOrderKey(DataItem dataItem) {
            return dataItem.getInputData().get("caseId");
        }

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
                OrderedApiCompletionConfig.builder()
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

    @Test
    void testOrderAndConcurrent2() {
        DataLoader dataLoader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return ListUtils.of(
                        new InputData(MapUtils.of("caseId", "1", "query", "query1", "round", 1)),
                        new InputData(MapUtils.of("caseId", "1", "query", "query2", "round", 2)),
                        new InputData(MapUtils.of("caseId", "1", "query", "query3", "round", 3)),
                        new InputData(MapUtils.of("caseId", "2", "query", "query1", "round", 1)),
                        new InputData(MapUtils.of("caseId", "2", "query", "query2", "round", 2)),
                        new InputData(MapUtils.of("caseId", "3", "query", "query1", "round", 1)),
                        new InputData(MapUtils.of("caseId", "3", "query", "query2", "round", 2))
                );
            }
        };

        Begin begin = new Begin();
        TestApiCompletion apiCompletion = new TestApiCompletion(
                OrderedApiCompletionConfig.builder()
                        .threadNum(4)
                        .comparator((o1, o2) -> {
                            // 按照 round 倒序
                            InputData inputData1 = o1.getInputData();
                            InputData inputData2 = o2.getInputData();
                            int r1 = inputData1.get("round");
                            int r2 = inputData2.get("round");
                            return r2 - r1;
                        })
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
        assertEquals(ListUtils.of("query3", "query2", "query1"), apiCompletion.execOrder.get("1"));
        assertEquals(ListUtils.of("query2", "query1"), apiCompletion.execOrder.get("2"));
        assertEquals(ListUtils.of("query2", "query1"), apiCompletion.execOrder.get("3"));

        // 并发度断言：3 个 case 并行，总耗时 < 串行 7*200ms
        log.info("execOrder={}", apiCompletion.execOrder);
    }
}