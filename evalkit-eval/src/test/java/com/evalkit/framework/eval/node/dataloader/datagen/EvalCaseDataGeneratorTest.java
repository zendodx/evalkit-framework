package com.evalkit.framework.eval.node.dataloader.datagen;

import com.evalkit.framework.eval.node.dataloader.datagen.config.EvalCaseDataGeneratorConfig;
import com.evalkit.framework.eval.node.dataloader.datagen.querygen.MockQueryGenerator;
import org.junit.jupiter.api.Test;

class EvalCaseDataGeneratorTest {
    @Test
    void test() throws Exception {
        MockQueryGenerator mockQueryGenerator = new MockQueryGenerator() {
            @Override
            public String prepareTemplateQuery() {
                return "{{between_chinese_holiday 20250815 20251101}} 去 {{city 河北省}}";
            }
        };

        EvalCaseDataGenerator evalCaseDataGenerator = new EvalCaseDataGenerator(
                EvalCaseDataGeneratorConfig.builder()
                        .queryGenerator(mockQueryGenerator)
                        .enableOutputFile(true)
                        .genCount(5)
                        .roundCount(5)
                        .randomRound(true)
                        .build()
        );
        evalCaseDataGenerator.prepareDataList();
    }
}