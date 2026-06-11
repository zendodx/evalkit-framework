package com.evalkit.framework.eval.node.dataloader;

import com.evalkit.framework.eval.node.dataloader.config.ApiDataLoaderConfig;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiDataLoaderTest {

    /**
     * 测试 ApiDataLoader 配置校验逻辑：host 为空时应抛出 IllegalArgumentException
     */
    @Test
    void testEmptyHostThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ApiDataLoader(
                    ApiDataLoaderConfig.builder()
                            .host("")
                            .api("/api/test")
                            .method("get")
                            .timeout(10)
                            .timeUnit(TimeUnit.SECONDS)
                            .build()
            ) {
                @Override
                public Map<String, Object> prepareBody() {
                    return Collections.emptyMap();
                }

                @Override
                public Map<String, String[]> prepareParam() {
                    return Collections.emptyMap();
                }

                @Override
                public Map<String, String> prepareHeader() {
                    return Collections.emptyMap();
                }

                @Override
                public String prepareJsonpath() {
                    return "$.data";
                }
            };
        }, "host 为空时构造应抛出 IllegalArgumentException");
    }

    /**
     * 测试 ApiDataLoader 配置校验逻辑：api 为空时应抛出 IllegalArgumentException
     */
    @Test
    void testEmptyApiThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ApiDataLoader(
                    ApiDataLoaderConfig.builder()
                            .host("http://localhost:8080")
                            .api("")
                            .method("get")
                            .timeout(10)
                            .timeUnit(TimeUnit.SECONDS)
                            .build()
            ) {
                @Override
                public Map<String, Object> prepareBody() {
                    return Collections.emptyMap();
                }

                @Override
                public Map<String, String[]> prepareParam() {
                    return Collections.emptyMap();
                }

                @Override
                public Map<String, String> prepareHeader() {
                    return Collections.emptyMap();
                }

                @Override
                public String prepareJsonpath() {
                    return "$.data";
                }
            };
        }, "api 为空时构造应抛出 IllegalArgumentException");
    }

    /**
     * 测试 ApiDataLoader 正常构建（不发起真实 HTTP 请求，只验证构造成功）
     */
    @Test
    void testConstructWithValidConfig() {
        ApiDataLoader apiDataLoader = new ApiDataLoader(
                ApiDataLoaderConfig.builder()
                        .host("http://localhost:8080")
                        .api("/api/data")
                        .method("get")
                        .timeout(10)
                        .timeUnit(TimeUnit.SECONDS)
                        .build()
        ) {
            @Override
            public Map<String, Object> prepareBody() {
                return Collections.emptyMap();
            }

            @Override
            public Map<String, String[]> prepareParam() {
                return Collections.emptyMap();
            }

            @Override
            public Map<String, String> prepareHeader() {
                return Collections.emptyMap();
            }

            @Override
            public String prepareJsonpath() {
                return "$.data";
            }
        };

        assertNotNull(apiDataLoader, "ApiDataLoader 实例不应为 null");
    }
}