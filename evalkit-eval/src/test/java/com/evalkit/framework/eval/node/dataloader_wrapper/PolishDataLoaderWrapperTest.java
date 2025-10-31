package com.evalkit.framework.eval.node.dataloader_wrapper;

import com.evalkit.framework.eval.node.dataloader_wrapper.config.PolishDataLoaderWrapperConfig;

class PolishDataLoaderWrapperTest {
    void test() {
        PolishDataLoaderWrapper polishDataLoaderWrapper = new PolishDataLoaderWrapper(
                PolishDataLoaderWrapperConfig.builder().build()
        ) {
            @Override
            public String selectField() {
                return "";
            }
        };
    }
}