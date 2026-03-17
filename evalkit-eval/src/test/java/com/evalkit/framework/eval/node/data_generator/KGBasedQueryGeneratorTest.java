package com.evalkit.framework.eval.node.data_generator;

import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.data_generator.config.KGBasedQueryGeneratorConfig;
import com.evalkit.framework.infra.service.llm.LLMService;
import com.evalkit.framework.infra.utils.DebugUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;

@Slf4j
class KGBasedQueryGeneratorTest {

    @Test
    public void test() throws Exception {
        String kgFilePath = "travel_demo/travel_kg.ttl";
        String scenarioConfigFilePath = "travel_demo/scenario_config.json";
        LLMService llmService = DebugUtils.buildLLMService();

        KGBasedQueryGenerator generator = new KGBasedQueryGenerator(
                KGBasedQueryGeneratorConfig.builder()
                        .scenarioConfigFilePath(scenarioConfigFilePath)
                        .kgFilePath(kgFilePath)
                        .llmService(llmService)
                        .enableOutputFile(true)
                        .generateCount(1)
                        .threadNum(1)
                        .build()
        );

        List<InputData> generated = generator.generateWrapper();
        log.debug("generated: {}", generated);
    }
}