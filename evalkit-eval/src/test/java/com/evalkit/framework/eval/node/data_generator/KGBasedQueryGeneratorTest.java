package com.evalkit.framework.eval.node.data_generator;

import com.evalkit.framework.common.utils.list.ListUtils;
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
        String scenarioConfigFilePath2 = "travel_demo/scenario2_config.json";
        LLMService llmService = DebugUtils.buildLLMService();

        KGBasedQueryGenerator generator = new KGBasedQueryGenerator(
                KGBasedQueryGeneratorConfig.builder()
                        .scenarioConfigFilePath(ListUtils.of(scenarioConfigFilePath, scenarioConfigFilePath2))
                        .kgFilePath(kgFilePath)
                        .llmService(llmService)
                        .enableOutputFile(true)
                        .generateCount(1)
                        .threadNum(1)
                        .sessionIdFieldName("session_id")
                        .turnFieldName("turn")
                        .queryFieldName("query")
                        .enableOneRawOneSession(false)
                        .build()
        );

        List<InputData> generated = generator.generateWrapper();
        log.debug("generated: {}", generated);
    }
}