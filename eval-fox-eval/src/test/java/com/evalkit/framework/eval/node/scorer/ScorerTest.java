package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.model.*;
import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.workflow.WorkflowContextHolder;
import com.evalkit.framework.workflow.model.WorkflowContext;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScorerTest {
    private static final InputData INPUT = new InputData();
    private static final ApiCompletionResult API = new ApiCompletionResult();

    static {
        INPUT.set("query", "hello");
        API.set("response", "world");
    }

    private final Scorer scorer = new Scorer() {
        @Override
        public ScorerResult eval(DataItem item) {
            String q = item.getInputData().get("query");
            String r = item.getApiCompletionResult().get("response");
            return ScorerResult.builder()
                    .metric("指标1")
                    .score(1.0)
                    .reason(q + ":" + r)
                    .build();
        }
    };

    @Test
    public void testDoExecute() {
        DataItem dataItem = spy(DataItem.builder().dataIndex(0L).inputData(INPUT).apiCompletionResult(API).build());

        try (MockedStatic<WorkflowContextHolder> ctxStatic = mockStatic(WorkflowContextHolder.class)) {
            WorkflowContext ctx = mock(WorkflowContext.class);
            ctxStatic.when(WorkflowContextHolder::get).thenReturn(ctx);
            when(WorkflowContextOps.getDataItems(ctx)).thenReturn(ListUtils.of(dataItem));

            scorer.doExecute();

            EvalResult result = dataItem.getEvalResult();
            assertNotNull(result);
            // 校验评估器数量
            assertTrue(CollectionUtils.isNotEmpty(result.getScorerResults()));
            // 校验最终评测结果
            assertEquals(1.0, result.getScore());
            assertEquals("[{\"评估指标\":\"指标1\",\"评估理由\":\"hello:world\"}]", result.getReason());
        }
    }

}