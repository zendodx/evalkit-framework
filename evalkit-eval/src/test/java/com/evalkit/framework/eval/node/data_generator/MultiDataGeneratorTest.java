package com.evalkit.framework.eval.node.data_generator;

import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.eval.node.data_generator.config.KGBasedQueryGeneratorConfig;
import com.evalkit.framework.eval.node.data_generator.config.MultiDataGeneratorConfig;
import com.evalkit.framework.infra.service.llm.LLMService;
import com.evalkit.framework.infra.utils.DebugUtils;
import org.junit.jupiter.api.Test;

class MultiDataGeneratorTest {
    @Test
    public void test() {
        String kgFilePath = "travel_demo/travel_kg.ttl";
        String scenarioConfigFilePath = "travel_demo/scenario_config.json";
        String scenario2ConfigFilePath = "travel_demo/scenario2_config.json";

        LLMService llmService = DebugUtils.buildLLMService();

        KGBasedQueryGenerator generator1 = new KGBasedQueryGenerator(
                KGBasedQueryGeneratorConfig.builder()
                        .scenarioConfigFilePath(scenarioConfigFilePath)
                        .kgFilePath(kgFilePath)
                        .llmService(llmService)
                        .enableOutputFile(true)
                        .generateCount(1)
                        .build()
        );

        KGBasedQueryGenerator generator2 = new KGBasedQueryGenerator(
                KGBasedQueryGeneratorConfig.builder()
                        .scenarioConfigFilePath(scenario2ConfigFilePath)
                        .kgFilePath(kgFilePath)
                        .llmService(llmService)
                        .enableOutputFile(true)
                        .generateCount(1)
                        .build()
        );


        MultiDataGenerator multiDataGenerator = new MultiDataGenerator(
                MultiDataGeneratorConfig.builder()
                        .dataGenerators(ListUtils.of(generator1, generator2))
                        .enableOutputFile(true)
                        .build()
        );
        multiDataGenerator.generateWrapper();
    }
}