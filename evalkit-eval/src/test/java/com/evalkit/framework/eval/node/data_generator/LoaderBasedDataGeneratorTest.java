package com.evalkit.framework.eval.node.data_generator;

import com.evalkit.framework.common.utils.convert.TypeConvertUtils;
import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.common.utils.random.UuidUtils;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.eval.node.dataloader.data_generator.LoaderBasedDataGenerator;
import com.evalkit.framework.eval.node.querygen.config.LoaderBasedDataGeneratorConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * 基于数据集加载器的数据生成器单测
 */
@Slf4j
class LoaderBasedDataGeneratorTest {

    DataLoader dataLoader;

    @BeforeEach
    public void setUp() {
        // 数据加载器,每行是一轮对话
        dataLoader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() throws Exception {
                return ListUtils.of(
                        new InputData(MapUtils.of("queries", "s1q1#s1q2")),
                        new InputData(MapUtils.of("queries", "s2q1#s2q2#s2q3"))
                );
            }
        };
    }


    @Test
    public void test() throws Exception {
        // 数据生成器, 将原始数据变成多轮对话, 每行是一个Query
        LoaderBasedDataGenerator generator = new LoaderBasedDataGenerator(
                LoaderBasedDataGeneratorConfig.builder()
                        .dataLoader(dataLoader)
                        .threadNum(2)
                        .build()
        ) {
            @Override
            public List<Map<String, Object>> processSingleInputData(Map<String, Object> inputItem) {
                String queries = TypeConvertUtils.toString(inputItem.getOrDefault("queries", null));
                if (StringUtils.isEmpty(queries)) {
                    return Collections.emptyList();
                }
                String[] split = StringUtils.split(queries, "#");
                if (split.length == 0) {
                    return Collections.emptyList();
                }
                List<Map<String, Object>> result = new ArrayList<>();
                String sessionId = UuidUtils.generateUuid();
                for (int i = 0; i < split.length; i++) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("sessionId", sessionId);
                    map.put("turn", i + 1);
                    map.put("query", split[i]);
                    result.add(map);
                }
                return result;
            }
        };

        // 验证
        log.info("raw input data: {}", dataLoader.loadWrapper());
        List<InputData> generateDataList = generator.prepareDataList();
        log.info("generated data: {}", generateDataList);
        Assertions.assertTrue(CollectionUtils.isNotEmpty(generateDataList));
        Assertions.assertEquals(5, generateDataList.size());
    }

    @Test
    public void testBadProcess() throws Exception {
        // 数据生成器, 将原始数据变成多轮对话, 每行是一个Query
        LoaderBasedDataGenerator generator = new LoaderBasedDataGenerator(
                LoaderBasedDataGeneratorConfig.builder()
                        .dataLoader(dataLoader)
                        .threadNum(2)
                        .build()
        ) {
            @Override
            public List<Map<String, Object>> processSingleInputData(Map<String, Object> inputItem) {
                int i = 1 / 0;
                return ListUtils.of(inputItem);
            }
        };

        // 验证
        log.info("raw input data: {}", dataLoader.loadWrapper());
        List<InputData> generateDataList = generator.prepareDataList();
        log.info("generated data: {}", generateDataList);
        Assertions.assertTrue(CollectionUtils.isEmpty(generateDataList));
    }
}