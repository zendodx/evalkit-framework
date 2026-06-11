package com.evalkit.framework.eval.node.api;

import com.evalkit.framework.common.client.http.model.HttpApiResponse;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.api.config.HttpApiCompletionConfig;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HttpApiCompletionTest {

    /**
     * 测试 HttpApiCompletion 可以正确构建并初始化，使用 localhost 作为 mock host
     * 不发起真实 HTTP 请求，只验证对象构建逻辑
     */
    @Test
    void testConstructAndBuildConfig() {
        HttpApiCompletion httpApiCompletion = new HttpApiCompletion(
                HttpApiCompletionConfig.builder()
                        .host("http://localhost:8080")
                        .api("/api/test")
                        .method("POST")
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
                return new ApiCompletionResult();
            }
        };

        assertNotNull(httpApiCompletion, "HttpApiCompletion 实例不应为 null");
        assertNotNull(httpApiCompletion.getConfig(), "HttpApiCompletion 配置不应为 null");
        HttpApiCompletionConfig config = (HttpApiCompletionConfig) httpApiCompletion.getConfig();
        assertEquals("http://localhost:8080", config.getHost(), "Host 应与构建时一致");
        assertEquals("/api/test", config.getApi(), "API 路径应与构建时一致");
    }
}