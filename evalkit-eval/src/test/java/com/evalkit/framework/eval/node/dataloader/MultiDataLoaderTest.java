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
class MultiDataLoaderTest {

    MultiDataLoader multiDataLoader;

    @BeforeEach
    void setUp() {
        DataLoader d1 = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() throws Exception {
                return ListUtils.of(
                        new InputData(MapUtils.of("query", "1"))
                );
            }
        };
        DataLoader d2 = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() throws Exception {
                return ListUtils.of(
                        new InputData(MapUtils.of("query", "2"))
                );
            }
        };
        multiDataLoader = new MultiDataLoader(ListUtils.of(d1, d2));
    }

    @Test
    public void testPrepareDataList() {
        List<InputData> inputData = multiDataLoader.prepareDataList();
        log.info("multi data loader: {}", inputData);
        Assertions.assertEquals(2, inputData.size());
    }
}