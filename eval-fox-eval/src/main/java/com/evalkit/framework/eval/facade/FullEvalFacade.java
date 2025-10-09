package com.evalkit.framework.eval.facade;

import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.EvalResult;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.workflow.Workflow;
import com.evalkit.framework.workflow.model.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 全量式评测
 * 最基础的评测,配置简单,中断后会重新开始
 */
@Slf4j
public class FullEvalFacade extends EvalFacade {
    /* 全量式评测配置 */
    protected final FullEvalConfig config;
    /* 工作流上下文 */
    protected WorkflowContext workflowContext;

    public FullEvalFacade(FullEvalConfig config) {
        this.config = config;
    }

    /**
     * 执行前初始化工作流上下文
     */
    @Override
    protected void init() {
        workflowContext = new WorkflowContext();
    }

    /**
     * 加载数据到工作流上下文
     */
    @Override
    protected void loadData() {
        // 加载数据
        DataLoader dataLoader = config.getDataLoader();
        List<InputData> inputDataList = dataLoader.loadWrapper();
        if (CollectionUtils.isEmpty(inputDataList)) {
            return;
        }
        // 构建dataItem
        List<DataItem> dataItems = new CopyOnWriteArrayList<>();
        inputDataList.forEach(inputData -> {
            DataItem dataItem = new DataItem();
            dataItem.setDataIndex(inputData.getDataIndex());
            dataItem.setInputData(inputData);
            dataItems.add(dataItem);
        });
        // dataItem存入上下文
        WorkflowContextOps.setDataItems(workflowContext, dataItems);
    }

    /**
     * 执行评测工作流
     */
    protected Object eval() {
        Workflow evalWorkflow = config.getEvalWorkflow();
        evalWorkflow.setWorkflowContext(workflowContext);
        evalWorkflow.execute();
        return null;
    }

    /**
     * 结果上报
     */
    protected void report() {
        Workflow reportWorkflow = config.getReportWorkflow();
        reportWorkflow.setWorkflowContext(workflowContext);
        reportWorkflow.execute();
    }

    /**
     * 获取待处理数据量
     */
    @Override
    public long getRemainDataCount() {
        if (workflowContext == null) {
            return 0;
        }
        List<DataItem> dataItems = WorkflowContextOps.getDataItems(workflowContext);
        List<DataItem> collect = dataItems.stream().filter(dataItem -> {
            InputData inputData = dataItem.getInputData();
            ApiCompletionResult apiCompletionResult = dataItem.getApiCompletionResult();
            EvalResult evalResult = dataItem.getEvalResult();
            return inputData != null && apiCompletionResult == null && evalResult == null;
        }).collect(Collectors.toList());
        return collect.size();
    }

    /**
     * 获取已处理数据量
     */
    @Override
    public long getProcessedDataCount() {
        if (workflowContext == null) {
            return 0;
        }
        List<DataItem> dataItems = WorkflowContextOps.getDataItems(workflowContext);
        List<DataItem> collect = dataItems.stream().filter(dataItem -> {
            InputData inputData = dataItem.getInputData();
            ApiCompletionResult apiCompletionResult = dataItem.getApiCompletionResult();
            EvalResult evalResult = dataItem.getEvalResult();
            return inputData != null && (apiCompletionResult != null || evalResult != null);
        }).collect(Collectors.toList());
        return collect.size();
    }
}
