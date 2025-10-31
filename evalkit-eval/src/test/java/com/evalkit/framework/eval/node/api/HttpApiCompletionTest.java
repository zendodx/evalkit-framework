package com.evalkit.framework.eval.node.api;

import com.evalkit.framework.common.client.http.model.HttpApiResponse;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.api.config.HttpApiCompletionConfig;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

class HttpApiCompletionTest {
    void test() {
        HttpApiCompletion httpApiCompletion = new HttpApiCompletion(
                HttpApiCompletionConfig.builder()
                        .host("")
                        .api("")
                        .method("")
                        .build()
        ) {
            @Override
            public Map<String, Object> prepareBody(InputData inputData) {
                return Collections.emptyMap();
            }

            @Override
            public Map<String, String[]> prepareParam(InputData inputData) {
                return Collections.emptyMap();
            }

            @Override
            public Map<String, String> prepareHeader(InputData inputData) {
                return Collections.emptyMap();
            }

            @Override
            public ApiCompletionResult buildApiCompletionResult(InputData inputData, HttpApiResponse response) {
                return null;
            }
        };
    }
}