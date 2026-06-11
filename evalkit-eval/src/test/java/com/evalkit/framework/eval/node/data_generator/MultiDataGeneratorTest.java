package com.evalkit.framework.eval.node.data_generator;

import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.eval.node.data_generator.config.KGBasedQueryGeneratorConfig;
import com.evalkit.framework.eval.node.data_generator.config.MultiDataGeneratorConfig;
import com.evalkit.framework.infra.service.llm.LLMService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MultiDataGeneratorTest {

    /**
     * 构造一个 mock LLMService，返回符合 Turn JSON 格式的内容
     */
    private LLMService buildMockLLMService() {
        return new LLMService() {
            @Override
            public String chat(String prompt) {
                // 返回合法的 Turn JSON 数组，供 KGBasedQueryGenerator 解析
                return "[" +
                        "{\"turn\":1,\"query\":\"我想了解一下旅游攻略\"}," +
                        "{\"turn\":2,\"query\":\"请推荐交通方式\"}," +
                        "{\"turn\":3,\"query\":\"有什么酒店推荐吗？\"}," +
                        "{\"turn\":4,\"query\":\"帮我预订一下。\"}" +
                        "]";
            }

            @Override
            public String getModel() {
                return "mock-model";
            }
        };
    }

    @Test
    public void test() {
        // 文件已存在于 classpath:travel_demo/，由 KGBasedQueryGenerator 自动从 classpath 加载
        String kgFilePath = "travel_demo/travel_kg.ttl";
        String scenarioConfigFilePath = "travel_demo/scenario_config.json";
        String scenario2ConfigFilePath = "travel_demo/scenario2_config.json";

        LLMService llmService = buildMockLLMService();

        KGBasedQueryGenerator generator1 = new KGBasedQueryGenerator(
                KGBasedQueryGeneratorConfig.builder()
                        .scenarioConfigFilePath(ListUtils.of(scenarioConfigFilePath))
                        .kgFilePath(kgFilePath)
                        .llmService(llmService)
                        .enableOutputFile(false)  // 关闭文件输出，避免在 CI 环境写文件
                        .generateCount(1)
                        .build()
        );

        KGBasedQueryGenerator generator2 = new KGBasedQueryGenerator(
                KGBasedQueryGeneratorConfig.builder()
                        .scenarioConfigFilePath(ListUtils.of(scenario2ConfigFilePath))
                        .kgFilePath(kgFilePath)
                        .llmService(llmService)
                        .enableOutputFile(false)
                        .generateCount(1)
                        .build()
        );

        MultiDataGenerator multiDataGenerator = new MultiDataGenerator(
                MultiDataGeneratorConfig.builder()
                        .dataGenerators(ListUtils.of(generator1, generator2))
                        .enableOutputFile(false)
                        .build()
        );

        // 调用并验证结果不为 null
        assertDoesNotThrow(multiDataGenerator::generateWrapper,
                "MultiDataGenerator 不应抛出异常");
    }
}