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
        LLMService llmService = DebugUtils.buildLLMService();

        KGBasedQueryGenerator generator1 = new KGBasedQueryGenerator(
                KGBasedQueryGeneratorConfig.builder()
                        .scenarioConfigFilePath("scenario_itinerary_to_booking.json")
                        .kgFilePath("travel.ttl")
                        .llmService(llmService)
                        .enableOutputFile(true)
                        .generateCount(1)
                        .build()
        );

        KGBasedQueryGenerator generator2 = new KGBasedQueryGenerator(
                KGBasedQueryGeneratorConfig.builder()
                        .scenarioConfigFilePath("scenario_cross_domain_booking.json")
                        .kgFilePath("jene_test.ttl")
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