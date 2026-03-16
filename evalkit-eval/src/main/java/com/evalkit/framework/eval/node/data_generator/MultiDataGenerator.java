package com.evalkit.framework.eval.node.data_generator;

import com.evalkit.framework.eval.node.data_generator.config.MultiDataGeneratorConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 多源数据生成器,集成多种数据生成器
 */
@Slf4j
public class MultiDataGenerator extends DataGenerator {
    protected MultiDataGeneratorConfig config;

    public MultiDataGenerator(MultiDataGeneratorConfig config) {
        super(config);
        this.config = config;
    }

    @Override
    protected List<Map<String, Object>> generate() throws Exception {
        List<DataGenerator> dataGenerators = config.getDataGenerators();
        if (CollectionUtils.isEmpty(dataGenerators)) {
            log.debug("[MultiDataGenerator] No data generator configured");
            return Collections.emptyList();
        }
        List<Map<String, Object>> res = new ArrayList<>();
        for (DataGenerator dataGenerator : dataGenerators) {
            res.addAll(dataGenerator.generate());
        }
        return res;
    }
}
