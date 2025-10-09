package com.evalkit.framework.eval.node.dataloader;

import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.dataloader.config.DataLoaderConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 多数据源加载器
 */
public class MultiDataLoader extends DataLoader {
    /* 数据加载器集合 */
    private final List<DataLoader> dataLoaders;

    public MultiDataLoader(List<DataLoader> dataLoaders) {
        super();
        this.dataLoaders = dataLoaders;
    }

    public MultiDataLoader(List<DataLoader> dataLoaders, int offset, int limit) {
        super(offset, limit);
        this.dataLoaders = dataLoaders;
    }

    public MultiDataLoader(List<DataLoader> dataLoaders, DataLoaderConfig config) {
        super(config);
        this.dataLoaders = dataLoaders;
    }

    @Override
    public List<InputData> prepareDataList() {
        List<InputData> inputDataList = new ArrayList<>();
        for (DataLoader dataLoader : dataLoaders) {
            List<InputData> inputData = dataLoader.loadWrapper();
            inputDataList.addAll(inputData);
        }
        updateIndex(inputDataList);
        return inputDataList;
    }

    private void updateIndex(List<InputData> inputDataList) {
        long index = 0;
        for (InputData inputData : inputDataList) {
            inputData.setDataIndex(index++);
        }
    }
}
