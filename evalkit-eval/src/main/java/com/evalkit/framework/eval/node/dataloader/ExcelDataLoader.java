package com.evalkit.framework.eval.node.dataloader;

import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.dataloader.config.ExcelDataLoaderConfig;
import com.evalkit.framework.common.utils.file.ExcelUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Excel数据加载器
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class ExcelDataLoader extends DataLoader {
    protected ExcelDataLoaderConfig config;

    public ExcelDataLoader(String filePath) {
        this(ExcelDataLoaderConfig.builder().filePath(filePath).build());
    }

    public ExcelDataLoader(String filePath, int offset, int limit) {
        this(ExcelDataLoaderConfig.builder().filePath(filePath).offset(offset).limit(limit).build());
    }

    public ExcelDataLoader(ExcelDataLoaderConfig config) {
        super(config);
        this.config = config;
    }

    @Override
    public List<InputData> prepareDataList() throws IOException {
        AtomicLong dataIndex = new AtomicLong(0L);
        List<Map<String, String>> items = ExcelUtils.readExcelAsListMapWithDefaultHeaders(config.getFilePath(), config.getSheetIndex(),
                config.getOffset(), config.getLimit());
        return items.stream().map(item -> {
            Map<String, Object> t = new HashMap<>(item.size());
            t.putAll(item);
            return new InputData(dataIndex.getAndIncrement(), t);
        }).collect(Collectors.toList());
    }

    /**
     * 加载时就已经截断,此时只需要返回评测数据即可
     */
    protected List<InputData> slice(List<InputData> inputDataList) {
        return inputDataList;
    }

    @Override
    protected void beforeLoad() {
        super.beforeLoad();
    }
}
