package com.evalkit.framework.eval.node.dataloader.datagen.querygen;

import com.evalkit.framework.eval.node.dataloader.datagen.querygen.config.MockerQueryGeneratorConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
class MockQueryGeneratorTest {
    @Test
    void test() {
        String templateQuery = "{{between_chinese_holiday 20250815 20251101}} 去 {{city 河北省}}";

        MockQueryGenerator mockQueryGenerator = new MockQueryGenerator(
                MockerQueryGeneratorConfig.builder().build()
        ) {
            @Override
            public String prepareTemplateQuery() {
                return templateQuery;
            }
        };

        String generateQuery = mockQueryGenerator.generate();
        log.info("template query: {}, generate query: {}", templateQuery, generateQuery);
    }
}