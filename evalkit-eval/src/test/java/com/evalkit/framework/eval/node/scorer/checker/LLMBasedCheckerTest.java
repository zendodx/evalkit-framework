package com.evalkit.framework.eval.node.scorer.checker;

import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.node.scorer.checker.config.LLMBasedCheckerConfig;
import com.evalkit.framework.eval.node.scorer.checker.model.CheckItem;
import com.evalkit.framework.infra.service.llm.LLMService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class LLMBasedCheckerTest {

    /**
     * 构造一个 mock LLMService，不依赖外部服务
     */
    private LLMService buildMockLLMService() {
        return new LLMService() {
            @Override
            public String chat(String prompt) {
                return "mock reply";
            }

            @Override
            public String getModel() {
                return "mock-model";
            }
        };
    }

    @Test
    void testConstructLLMBasedChecker() {
        LLMBasedChecker checker = new LLMBasedChecker(
                LLMBasedCheckerConfig.builder()
                        .llmService(buildMockLLMService())
                        .build()
        ) {
            @Override
            protected List<CheckItem> prepareCheckItems(DataItem dataItem) {
                return Collections.emptyList();
            }

            @Override
            protected String prepareUserPrompt(DataItem dataItem, int round) {
                return "";
            }

            @Override
            protected boolean needCheck(DataItem dataItem, int round) {
                return false;
            }

            @Override
            public boolean support(DataItem dataItem) {
                return false;
            }

            @Override
            public double getTotalScore() {
                return 0;
            }
        };

        assertNotNull(checker, "LLMBasedChecker 实例不应为 null");
    }
}