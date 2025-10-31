package com.evalkit.framework.eval.node.scorer.checker;

import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.node.scorer.checker.config.LLMBasedCheckerConfig;
import com.evalkit.framework.eval.node.scorer.checker.model.CheckItem;
import com.evalkit.framework.infra.service.llm.LLMServiceFactory;

import java.util.Collections;
import java.util.List;

class LLMBasedCheckerTest {
    void test() {
        LLMBasedChecker checker = new LLMBasedChecker(
                LLMBasedCheckerConfig.builder()
                        .llmService(LLMServiceFactory.createLLMService("test", null))
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
    }
}