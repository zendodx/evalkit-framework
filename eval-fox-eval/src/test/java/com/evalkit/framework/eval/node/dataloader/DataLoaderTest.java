package com.evalkit.framework.eval.node.dataloader;

import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.scorer.strategy.SumScoreStrategy;
import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.workflow.WorkflowContextHolder;
import com.evalkit.framework.workflow.model.WorkflowContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DataLoaderTest {
    /**
     * 构造数据，避免写在测试方法里
     **/
    private static final InputData D1 = new InputData(MapUtils.of("query", "hello"));
    private static final InputData D2 = new InputData(MapUtils.of("query", "world"));

    /**
     * 被测对象：匿名子类，数据固定
     **/
    private final DataLoader dataLoader = new DataLoader() {
        @Override
        public List<InputData> prepareDataList() {
            return ListUtils.of(D1, D2);
        }
    };

    @Test
    public void testDoExecute() {
        /* 准备局部 mock + 静态 mock */
        try (MockedStatic<WorkflowContextHolder> ctxHolder = mockStatic(WorkflowContextHolder.class)) {
            WorkflowContext ctx = mock(WorkflowContext.class);
            List<DataItem> dataItems = mock(List.class);

            ctxHolder.when(WorkflowContextHolder::get).thenReturn(ctx);
            when(WorkflowContextOps.getDataItems(ctx)).thenReturn(dataItems);
            when(WorkflowContextOps.getThreshold(ctx)).thenReturn(0.75);
            when(WorkflowContextOps.getScorerStrategy(ctx)).thenReturn(new SumScoreStrategy());

            /* 执行 */
            dataLoader.doExecute();

            /* 验证 + 断言 */
            ArgumentCaptor<DataItem> captor = ArgumentCaptor.forClass(DataItem.class);
            verify(dataItems, times(2)).add(captor.capture());

            List<DataItem> captured = captor.getAllValues();
            assertEquals(2, captured.size());
            assertEquals(0, captured.get(0).getDataIndex());
            assertEquals(1, captured.get(1).getDataIndex());
            // 额外：确认业务字段也正确
            assertEquals("hello", captured.get(0).getInputData().get("query"));
            assertEquals("world", captured.get(1).getInputData().get("query"));
        }
    }
}