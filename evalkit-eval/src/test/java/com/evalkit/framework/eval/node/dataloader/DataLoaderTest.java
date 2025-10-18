package com.evalkit.framework.eval.node.dataloader;

import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.eval.model.InputData;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

@Slf4j
class DataLoaderTest {

    private DataLoader dataLoader;

    @BeforeEach
    void setUp() {
        dataLoader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() throws Exception {
                return ListUtils.of(
                        new InputData(MapUtils.of("query", "1")),
                        new InputData(MapUtils.of("query", "2"))
                );
            }
        };
    }

    @Test
    void loadWrapper() {
        List<InputData> inputData = dataLoader.loadWrapper();
        log.info("inputData:{}", inputData);
        Assertions.assertEquals(2, inputData.size());
    }
}