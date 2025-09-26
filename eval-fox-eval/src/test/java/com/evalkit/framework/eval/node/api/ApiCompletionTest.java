package com.evalkit.framework.eval.node.api;

import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.workflow.WorkflowContextHolder;
import com.evalkit.framework.workflow.model.WorkflowContext;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiCompletionTest {
    /* 构造数据 */
    private static final InputData D1 = new InputData(MapUtils.of("query", "hello"));
    private static final InputData D2 = new InputData(MapUtils.of("query", "world"));
    private static final List<DataItem> dataItems = Arrays.asList(
            new DataItem(0L, D1),
            new DataItem(1L, D2)
    );

    private final ApiCompletion apiCompletion = new ApiCompletion() {
        @Override
        protected ApiCompletionResult invoke(DataItem dataItem) throws IOException {
            InputData inputData = dataItem.getInputData();
            String query = inputData.get("query");
            Map<String, Object> resultItem = MapUtils.of("response", "response for:" + query);
            return new ApiCompletionResult(resultItem);
        }
    };

    @Test
    public void testDoExecute() {
        try (MockedStatic<WorkflowContextHolder> ctxHolder = mockStatic(WorkflowContextHolder.class)) {
            // 准备
            WorkflowContext ctx = mock(WorkflowContext.class);
            ctxHolder.when(WorkflowContextHolder::get).thenReturn(ctx);
            when(WorkflowContextOps.getDataItems(ctx)).thenReturn(dataItems);

            // 执行
            apiCompletion.doExecute();

            // 验证
            assertEquals(dataItems.size(), 2);
            dataItems.forEach(dataItem -> {
                ApiCompletionResult completionResult = dataItem.getApiCompletionResult();
                assertNotNull(completionResult);
                assertTrue(completionResult.isSuccess());
                InputData inputData = dataItem.getInputData();
                String query = inputData.get("query");
                String response = completionResult.get("response");
                assertTrue(StringUtils.isNotEmpty(response) && StringUtils.equals(response, "response for:" + query));
            });
        }
    }
}