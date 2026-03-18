package com.evalkit.framework.eval.node.data_generator;

import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.EvalResult;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.data_generator.config.DataGeneratorConfig;
import com.evalkit.framework.eval.node.data_generator.exporter.GenDataExporter;
import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.eval.node.scorer.strategy.EvalReasonStrategy;
import com.evalkit.framework.eval.node.scorer.strategy.ScoreStrategy;
import com.evalkit.framework.workflow.model.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据生成器基类
 */
@Slf4j
public abstract class DataGenerator extends DataLoader {
    DataGeneratorConfig config;

    public DataGenerator(DataGeneratorConfig config) {
        this.config = config;
    }

    @Override
    protected void doExecute() {
        try {
            long start = System.currentTimeMillis();
            // 生成数据
            List<InputData> inputDataList = generateWrapper();
            // 给生成后的数据添加索引
            addDataIndex(inputDataList);
            if (CollectionUtils.isEmpty(inputDataList)) {
                throw new EvalException("Generate input data list is empty");
            }
            WorkflowContext ctx = getWorkflowContext();
            List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
            double threshold = WorkflowContextOps.getThreshold(ctx);
            ScoreStrategy scoreStrategy = WorkflowContextOps.getScorerStrategy(ctx);
            EvalReasonStrategy evalReasonStrategy = WorkflowContextOps.getEvalReasonStrategy(ctx);
            inputDataList.forEach(inputData -> dataItems.add(buildDataItem(inputData.getDataIndex(), inputData, threshold, scoreStrategy, evalReasonStrategy)));
            log.info("Generate data success, data size: {}, time cost: {}ms", inputDataList.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new EvalException("Generate eval data error:" + e.getMessage(), e);
        }
    }

    protected List<InputData> generateWrapper() {
        List<InputData> inputDataList = null;
        try {
            beforeGenerate();
            inputDataList = prepareDataList();
            return afterGenerate(inputDataList);
        } catch (Throwable e) {
            log.error("Generate eval data error:{}", e.getMessage(), e);
            onGenerateError(inputDataList, e);
            return null;
        }
    }

    protected void beforeGenerate() {

    }

    protected abstract List<Map<String, Object>> generate() throws Exception;

    protected List<InputData> afterGenerate(List<InputData> inputDataList) {
        return inputDataList;
    }

    protected void onGenerateError(List<InputData> inputDataList, Throwable e) {

    }

    public List<InputData> prepareDataList() throws Exception {
        List<InputData> inputDataList = new ArrayList<>();
        List<Map<String, Object>> dataList = generate();
        for (Map<String, Object> data : dataList) {
            // 过滤掉空数据
            if (data == null) {
                continue;
            }
            // 如果字段值是null会报空指针错误,设置为""
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                Object value = entry.getValue();
                if (value == null) {
                    entry.setValue("");
                }
            }
            InputData inputData = new InputData(data);
            inputDataList.add(inputData);
        }
        // 开启文件导出
        if (config.isEnableOutputFile() && CollectionUtils.isNotEmpty(config.getGenDataExporterList())) {
            // 如果没有导出文件夹则先创建
            String outputFilePath = config.getOutputFilePath();
            String outputFileName = config.getOutputFileName();
            Path gendata = Paths.get(outputFilePath + "/");
            if (!gendata.toFile().exists()) {
                gendata.toFile().mkdirs();
            }
            List<GenDataExporter> genDataExporterList = config.getGenDataExporterList();
            for (GenDataExporter genDataExporter : genDataExporterList) {
                String exportFile = genDataExporter.export(inputDataList, outputFilePath, outputFileName);
                log.info("Export gen data success, export file: {} ", exportFile);
            }
        }
        return inputDataList;
    }


    /**
     * 构建数据项,填充评测数据,初始化评测结果
     */
    protected DataItem buildDataItem(Long dataIndex, InputData inputData, double threshold, ScoreStrategy scoreStrategy, EvalReasonStrategy evalReasonStrategy) {
        DataItem dataItem = new DataItem(dataIndex, inputData);
        EvalResult evalResult = new EvalResult();
        evalResult.setThreshold(threshold);
        evalResult.setScoreStrategy(scoreStrategy);
        evalResult.setScoreStrategyName(scoreStrategy.getStrategyName());
        evalResult.setEvalReasonStrategy(evalReasonStrategy);
        evalResult.setEvalReasonStrategyName(evalReasonStrategy.getStrategyName());
        dataItem.setEvalResult(evalResult);
        return dataItem;
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
}