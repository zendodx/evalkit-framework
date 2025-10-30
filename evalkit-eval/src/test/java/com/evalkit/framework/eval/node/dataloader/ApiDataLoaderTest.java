package com.evalkit.framework.eval.node.dataloader;

import com.evalkit.framework.eval.node.dataloader.config.ApiDataLoaderConfig;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class ApiDataLoaderTest {

    void test() {
        ApiDataLoader apiDataLoader = new ApiDataLoader(
                ApiDataLoaderConfig.builder()
                        .host("")
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
    }

}