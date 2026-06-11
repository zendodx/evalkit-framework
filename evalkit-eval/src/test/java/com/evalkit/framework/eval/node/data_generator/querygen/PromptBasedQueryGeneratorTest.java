package com.evalkit.framework.eval.node.data_generator.querygen;

import com.evalkit.framework.eval.node.querygen.PromptBasedQueryGenerator;
import com.evalkit.framework.eval.node.querygen.config.PromptBasedQueryGeneratorConfig;
import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
class PromptBasedQueryGeneratorTest {

    /**
     * 构造一个 mock LLMService，返回换行分隔的 Query 列表（PromptBasedQueryGenerator 按 \n 分割回复）
     */
    private LLMService buildMockLLMService() {
        return new LLMService() {
            @Override
            public String chat(String prompt) {
                // 返回多行文本，模拟 LLM 生成 Query 的格式（每行一条 Query）
                return "如何快速预订机票\n机票价格最低查询\n最近热门旅游目的地推荐";
            }

            @Override
            public String getModel() {
                return "mock-model";
            }
        };
    }

    @Test
    void test() {
        LLMService llmService = buildMockLLMService();

        PromptBasedQueryGenerator promptBasedQueryGenerator = new PromptBasedQueryGenerator(
                PromptBasedQueryGeneratorConfig.builder()
                        .llmService(llmService)
                        .genCount(2)
                        .userPrompt("关键词: 预订机票")
                        .build()
        );
        List<String> queries = promptBasedQueryGenerator.generate();

        assertNotNull(queries, "生成的 queries 不应为 null");
        assertFalse(queries.isEmpty(), "生成的 queries 不应为空");
        log.info("queries: {}", queries);
    }

    @Test
    void testCustomSysPrompt() {
        LLMService llmService = buildMockLLMService();

        PromptBasedQueryGenerator generator = new PromptBasedQueryGenerator(
                PromptBasedQueryGeneratorConfig.builder()
                        .llmService(llmService)
                        .sysPrompt("你是一个Query生成助手，请生成简短的用户查询")
                        .userPrompt("关键词: 酒店预订")
                        .genCount(3)
                        .langStyle("简洁直接")
                        .build()
        );

        List<String> queries = generator.generate();
        assertNotNull(queries, "使用自定义 sysPrompt 生成的 queries 不应为 null");
        log.info("customSysPrompt queries: {}", queries);
    }
}