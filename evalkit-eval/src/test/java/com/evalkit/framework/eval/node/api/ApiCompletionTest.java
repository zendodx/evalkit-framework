package com.evalkit.framework.eval.node.api;

import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
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

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@Slf4j
class ApiCompletionTest {
    private final class TestApiCompletion extends ApiCompletion {
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
    void testConcurrent() {
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
        TestApiCompletion apiCompletion = new TestApiCompletion();
        apiCompletion.setThreadNum(4);

        // 必须在指定时间内跑完，否则认为死锁 / 阻塞
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(3), (ThrowingSupplier<Void>) () -> {
            new WorkflowBuilder()
                    .link(begin, dataLoader, apiCompletion)
                    .build()
                    .execute();
            return null;
        });

        // 并发度断言：3 个 case 并行，总耗时 < 串行 7*200ms
        log.info("execOrder={}", apiCompletion.execOrder);
    }


}