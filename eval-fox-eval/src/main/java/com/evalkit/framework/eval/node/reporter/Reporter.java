package com.evalkit.framework.eval.node.reporter;

import com.evalkit.framework.eval.constants.NodeNamePrefix;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.ReportData;
import com.evalkit.framework.workflow.WorkflowContextHolder;
import com.evalkit.framework.workflow.model.WorkflowContext;
import com.evalkit.framework.workflow.model.WorkflowNode;
import com.evalkit.framework.workflow.utils.WorkflowUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;


/**
 * 结果上报器
 */
@Slf4j
public abstract class Reporter extends WorkflowNode {
    public Reporter() {
        super(WorkflowUtils.generateNodeId(NodeNamePrefix.REPORTER));
    }

    /**
     * 上报前钩子
     */
    protected void beforeReport(ReportData reportData) {
    }

    /**
     * 上报
     */
    protected abstract void report(ReportData reportData) throws IOException;

    /**
     * 上报后钩子
     */
    protected void afterReport(ReportData reportData) {
    }

    /**
     * 错误处理钩子
     */
    protected void onErrorReport(ReportData reportData, Throwable e) {
    }

    /**
     * 包含钩子的上报,执行异常不会阻塞工作流运行
     */
    protected void reportWrapper(ReportData reportData) {
        try {
            beforeReport(reportData);
            report(reportData);
            afterReport(reportData);
        } catch (Throwable e) {
            log.error("Report error", e);
            onErrorReport(reportData, e);
        }
    }

    @Override
    public void doExecute() {
        long start = System.currentTimeMillis();
        WorkflowContext ctx = WorkflowContextHolder.get();
        // 构造上报对象
        List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
        Map<String, String> countResultMap = WorkflowContextOps.getCountResults(ctx);
        ReportData reportData = new ReportData(dataItems, countResultMap);
        if (CollectionUtils.isEmpty(dataItems)) {
            throw new EvalException("Data items is empty");
        }
        reportWrapper(reportData);
        log.info("Report success, time cost：{}ms", System.currentTimeMillis() - start);
    }
}
