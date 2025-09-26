package com.evalkit.framework.eval.node.dataloader_wrapper;

import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.workflow.WorkflowContextHolder;
import com.evalkit.framework.workflow.model.WorkflowContext;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class DataLoaderWrapperTest {
    /* 构造数据 */
    private static final InputData D1 = new InputData(MapUtils.of("query", "hello"));
    private static final InputData D2 = new InputData(MapUtils.of("query", "world"));
    private static final List<DataItem> dataItems = Arrays.asList(
            new DataItem(0L, D1),
            new DataItem(1L, D2)
    );

    private final DataLoaderWrapper wrapper = new DataLoaderWrapper() {
        @Override
        protected void wrapper(DataItem dataItem) {
            String prefix = "Mock:";
            InputData inputData = dataItem.getInputData();
            inputData.set("query", prefix + inputData.get("query"));
        }
    };

    @Test
    public void testDoExecute() {
        try (MockedStatic<WorkflowContextHolder> ctxHolder = mockStatic(WorkflowContextHolder.class)) {
            WorkflowContext ctx = mock(WorkflowContext.class);

            // 打桩
            ctxHolder.when(WorkflowContextHolder::get).thenReturn(ctx);
            when(WorkflowContextOps.getDataItems(ctx)).thenReturn(dataItems);

            // 执行
            wrapper.doExecute();

            // 验证
            assertEquals(dataItems.size(), 2);
            dataItems.forEach(item -> assertTrue(StringUtils.startsWith(item.getInputData().get("query"), "Mock:")));
        }
    }
}