package com.evalkit.framework.eval.node.dataloader;


import com.evalkit.framework.eval.constants.NodeNamePrefix;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.EvalResult;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.dataloader.config.DataLoaderConfig;
import com.evalkit.framework.eval.node.scorer.strategy.ScoreStrategy;
import com.evalkit.framework.workflow.model.WorkflowContext;
import com.evalkit.framework.workflow.model.WorkflowNode;
import com.evalkit.framework.workflow.utils.WorkflowUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;


/**
 * 数据加载器
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public abstract class DataLoader extends WorkflowNode {
    /* 数据加载配置 */
    protected DataLoaderConfig config;

    public DataLoader() {
        this(DataLoaderConfig.builder().build());
    }

    public DataLoader(int offset, int limit) {
        this(DataLoaderConfig.builder().offset(offset).limit(limit).build());
    }

    public DataLoader(DataLoaderConfig config) {
        super(WorkflowUtils.generateNodeId(NodeNamePrefix.DATA_LOADER));
        this.config = config;
    }

    /**
     * 添加过滤器
     */
    public void addFilter(Predicate<InputData> filter) {
        if (filter == null) {
            return;
        }
        config.getFilters().add(filter);
    }

    /**
     * 批量添加过滤器
     */
    public void addFilters(List<Predicate<InputData>> filters) {
        config.getFilters().addAll(filters);
    }

    /**
     * 设置页码和页数
     */
    public void setOffsetAndLimit(int offset, int limit) {
        config.setOffset(offset);
        config.setLimit(limit);
    }

    /**
     * 准备评测数据
     */
    public abstract List<InputData> prepareDataList() throws Exception;

    /**
     * 截断
     */
    protected List<InputData> slice(List<InputData> inputDataList) {
        if (config.getLimit() < 0 || CollectionUtils.isEmpty(inputDataList)) {
            return inputDataList;
        }
        int total = inputDataList.size();
        int fromIndex = Math.min(config.getOffset(), total);
        fromIndex = Math.max(0, fromIndex);
        int toIndex = Math.min(config.getOffset() + config.getLimit(), total);
        toIndex = Math.max(fromIndex, toIndex);
        return inputDataList.subList(fromIndex, toIndex);
    }

    /**
     * 过滤
     */
    protected void filter(List<InputData> inputDataList) {
        List<Predicate<InputData>> filters = config.getFilters();
        if (CollectionUtils.isEmpty(filters) || CollectionUtils.isEmpty(inputDataList)) {
            return;
        }
        inputDataList.removeIf(item -> filters.stream().anyMatch(filter -> !filter.test(item)));
    }

    /**
     * 打乱顺序
     */
    protected void shuffle(List<InputData> inputDataList) {
        Collections.shuffle(inputDataList);
    }

    /**
     * 评测数据加索引
     */
    protected void addDataIndex(List<InputData> inputDataList) {
        if (CollectionUtils.isEmpty(inputDataList)) {
            return;
        }
        long index = 0L;
        for (InputData inputData : inputDataList) {
            inputData.setDataIndex(index++);
        }
    }

    /**
     * 加载
     */
    protected List<InputData> load() throws Exception {
        List<InputData> rawInputDataList = prepareDataList();
        if (CollectionUtils.isEmpty(rawInputDataList)) {
            throw new EvalException("Input data list is empty");
        }
        // 传入的评测列表可能是不可变的,后续可能要修改,需转存成可变列表
        List<InputData> inputDatas = new CopyOnWriteArrayList<>(rawInputDataList);
        if (config.isShuffle()) {
            shuffle(inputDatas);
        }
        inputDatas = slice(inputDatas);
        filter(inputDatas);
        // 必须给每个数据项加索引
        addDataIndex(inputDatas);
        return inputDatas;
    }

    /**
     * 加载前钩子
     */
    protected void beforeLoad() {

    }

    /**
     * 加载后钩子
     */
    protected List<InputData> afterLoad(List<InputData> inputDataList) {
        return inputDataList;
    }

    /**
     * 错误处理钩子
     */
    protected void onLoadError(List<InputData> inputDataList, Throwable e) {

    }

    /**
     * 包含钩子的数据加载
     */
    public List<InputData> loadWrapper() {
        List<InputData> inputDataList = null;
        try {
            beforeLoad();
            inputDataList = load();
            return afterLoad(inputDataList);
        } catch (Throwable e) {
            onLoadError(inputDataList, e);
            return null;
        }
    }

    @Override
    protected void doExecute() {
        try {
            long start = System.currentTimeMillis();
            List<InputData> inputDataList = loadWrapper();
            if (CollectionUtils.isEmpty(inputDataList)) {
                throw new EvalException("Input data list is empty");
            }
            WorkflowContext ctx = getWorkflowContext();
            List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
            double threshold = WorkflowContextOps.getThreshold(ctx);
            ScoreStrategy scoreStrategy = WorkflowContextOps.getScorerStrategy(ctx);
            inputDataList.forEach(inputData -> dataItems.add(buildDataItem(inputData.getDataIndex(), inputData, threshold, scoreStrategy)));
            log.info("Load data success, data size: {}, time cost: {}ms", inputDataList.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new EvalException("Load eval data error:" + e.getMessage(), e);
        }
    }

    /**
     * 构建数据项,填充评测数据,初始化评测结果
     */
    protected DataItem buildDataItem(Long dataIndex, InputData inputData, double threshold, ScoreStrategy scoreStrategy) {
        DataItem dataItem = new DataItem(dataIndex, inputData);
        EvalResult evalResult = new EvalResult();
        evalResult.setThreshold(threshold);
        evalResult.setScoreStrategy(scoreStrategy);
        evalResult.setScoreStrategyName(scoreStrategy.getStrategyName());
        dataItem.setEvalResult(evalResult);
        return dataItem;
    }
}
