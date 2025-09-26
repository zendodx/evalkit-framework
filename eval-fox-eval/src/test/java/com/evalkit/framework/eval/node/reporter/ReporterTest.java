package com.evalkit.framework.eval.node.reporter;

import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.EvalResult;
import com.evalkit.framework.eval.model.ReportData;
import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.workflow.WorkflowContextHolder;
import com.evalkit.framework.workflow.model.WorkflowContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

class ReporterTest {
    private static final Logger log = LogManager.getLogger(ReporterTest.class);

    private final Reporter reporter = new Reporter() {
        @Override
        protected void report(ReportData reportData) throws IOException {
            List<DataItem> dataItems = reportData.getDataItems();
            Map<String, String> countResultMap = reportData.getCountResultMap();
            log.info("data items: {}, count result: {}", dataItems, countResultMap);
        }
    };

    @Test
    public void testDoExecute() {
        try (MockedStatic<WorkflowContextHolder> ctxHolder = mockStatic(WorkflowContextHolder.class)) {
            DataItem dataItem = spy(DataItem.builder()
                    .dataIndex(0L)
                    .evalResult(EvalResult.builder().build())
                    .build()
            );
            WorkflowContext ctx = mock(WorkflowContext.class);
            ctxHolder.when(WorkflowContextHolder::get).thenReturn(ctx);
            when(WorkflowContextOps.getDataItems(ctx)).thenReturn(ListUtils.of(dataItem));

            reporter.doExecute();
        }
    }
}