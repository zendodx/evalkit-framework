package com.evalkit.framework.eval.node.data_generator;

import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.data_generator.config.KGBasedQueryGeneratorConfig;
import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
class KGBasedQueryGeneratorTest {

    /**
     * 构造一个 mock LLMService，返回符合 Turn JSON 格式的内容：
     * KGBasedQueryGenerator 期望 LLM 返回 List<Turn> 的 JSON 数组
     */
    private LLMService buildMockLLMService() {
        return new LLMService() {
            @Override
            public String chat(String prompt) {
                // 返回合法的 Turn JSON 数组，匹配 scenario_config.json 中定义的 4 轮对话
                return "[" +
                        "{\"turn\":1,\"query\":\"打算带孩子去北京玩，有什么必看景点推荐吗？\"}," +
                        "{\"turn\":2,\"query\":\"从上海出发，有什么推荐的交通方式吗？\"}," +
                        "{\"turn\":3,\"query\":\"到了那边晚上住哪里比较方便？\"}," +
                        "{\"turn\":4,\"query\":\"帮我把刚才看好的车票预订一下。\"}" +
                        "]";
            }

            @Override
            public String getModel() {
                return "mock-model";
            }
        };
    }

    @Test
    public void test() throws Exception {
        // 文件已存在于 classpath:travel_demo/，由 KGBasedQueryGenerator 自动从 classpath 加载
        String kgFilePath = "travel_demo/travel_kg.ttl";
        String scenarioConfigFilePath = "travel_demo/scenario_config.json";
        String scenarioConfigFilePath2 = "travel_demo/scenario2_config.json";
        LLMService llmService = buildMockLLMService();

        KGBasedQueryGenerator generator = new KGBasedQueryGenerator(
                KGBasedQueryGeneratorConfig.builder()
                        .scenarioConfigFilePath(ListUtils.of(scenarioConfigFilePath, scenarioConfigFilePath2))
                        .kgFilePath(kgFilePath)
                        .llmService(llmService)
                        .enableOutputFile(false)  // 关闭文件输出，避免在 CI 环境写文件
                        .generateCount(1)
                        .threadNum(1)
                        .sessionIdFieldName("session_id")
                        .turnFieldName("turn")
                        .queryFieldName("query")
                        .enableOneRawOneSession(false)
                        .build()
        );

        List<InputData> generated = generator.generateWrapper();
        assertNotNull(generated, "生成的数据列表不应为 null");
        log.debug("generated count: {}, data: {}", generated.size(), generated);
    }
}