package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.scorer.config.DifyWorkflowScorerConfig;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

class DifyWorkflowScorerTest {
    void test() {
        DifyWorkflowScorer difyWorkflowScorer = new DifyWorkflowScorer(
                DifyWorkflowScorerConfig.builder().build()
        ) {
            @Override
            public Map<String, Object> prepareInputParams(InputData inputData, ApiCompletionResult apiCompletionResult) {
                return Collections.emptyMap();
            }

            @Override
            public ScorerResult prepareScorerResult(InputData inputData, ApiCompletionResult apiCompletionResult, Map<String, Object> outputs) {
                return null;
            }
        };
    }
}