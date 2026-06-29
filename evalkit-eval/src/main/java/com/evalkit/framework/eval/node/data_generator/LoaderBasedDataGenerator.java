package com.evalkit.framework.eval.node.data_generator;

import com.evalkit.framework.common.thread.BatchRunner;
import com.evalkit.framework.common.thread.PoolName;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.eval.node.querygen.config.LoaderBasedDataGeneratorConfig;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于数据集加载器的数据生成器
 * 场景: 先通过数据加载器查询数据,但此时数据可能不符合预期,需要二次加工
 */
public abstract class LoaderBasedDataGenerator extends DataGenerator {
    protected final LoaderBasedDataGeneratorConfig config;

    public LoaderBasedDataGenerator(LoaderBasedDataGeneratorConfig config) {
        super(config);
        validConfig(config);
        this.config = config;
    }

    protected void validConfig(LoaderBasedDataGeneratorConfig config) {
        DataLoader dataLoader = config.getDataLoader();
        if (dataLoader == null) {
            throw new IllegalArgumentException("[LoaderBasedDataGenerator] dataLoader is null");
        }
        if (config.getThreadNum() <= 0) {
            throw new IllegalArgumentException("[LoaderBasedDataGenerator] threadNum is invalid");
        }
    }

    @Override
    public List<Map<String, Object>> generate() {
        DataLoader dataLoader = config.getDataLoader();
        List<InputData> rawInputDataList = dataLoader.loadWrapper();
        if (CollectionUtils.isEmpty(rawInputDataList)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> inputItemList = rawInputDataList.stream().map(InputData::getInputItem).collect(Collectors.toList());
        // 并发处理每行数据
        List<List<Map<String, Object>>> singleSessionResultList = BatchRunner.runBatch(inputItemList, this::processSingleInputData, PoolName.DATA_GENERATOR, config.getThreadNum(), size -> size * config.getBatchTimeoutSec());
        if (CollectionUtils.isEmpty(singleSessionResultList)) {
            return Collections.emptyList();
        }
        return singleSessionResultList.stream().flatMap(List::stream).collect(Collectors.toList());
    }

    public abstract List<Map<String, Object>> processSingleInputData(Map<String, Object> inputItem);
}
