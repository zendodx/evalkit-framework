package com.evalkit.framework.eval.node.dataloader;

import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.dataloader.config.CsvDataLoaderConfig;
import com.evalkit.framework.common.utils.file.CsvUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Csv数据加载器
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class CsvDataLoader extends DataLoader {
    protected CsvDataLoaderConfig config;

    public CsvDataLoader(String filePath) {
        this(CsvDataLoaderConfig.builder().filePath(filePath).build());
    }

    public CsvDataLoader(String filePath, int offset, int limit) {
        this(CsvDataLoaderConfig.builder().filePath(filePath).offset(offset).limit(limit).build());
    }

    public CsvDataLoader(CsvDataLoaderConfig config) {
        super(config);
        this.config = config;
    }

    @Override
    public List<InputData> prepareDataList() {
        AtomicLong dataIndex = new AtomicLong(0L);
        List<Map<String, Object>> items = CsvUtils.readCsv(config.getFilePath(), config.getDelimiter(),
                config.isHasHeader(), config.getOffset(), config.getLimit());
        if (CollectionUtils.isEmpty(items)) {
            throw new EvalException("Csv Data is empty");
        }
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
