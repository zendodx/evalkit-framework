package com.evalkit.framework.eval.node.dataloader;

import org.junit.jupiter.api.Test;

class JsonTextDataLoaderTest {

    void test() {
        JsonTextDataLoader jsonTextDataLoader = new JsonTextDataLoader() {
            @Override
            public String prepareJsonpath() {
                return "$";
            }

            @Override
            public String prepareJson() {
                return "{\"query\":\"hello\"}";
            }
        };
    }
}