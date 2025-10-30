package com.evalkit.framework.eval.node.dataloader_wrapper;

import com.evalkit.framework.eval.node.dataloader_wrapper.config.DataLoaderWrapperConfig;

class PromptDataLoaderWrapperTest {
    void test() {
        PromptDataLoaderWrapper promptDataLoaderWrapper = new PromptDataLoaderWrapper(
                DataLoaderWrapperConfig.builder().build()
        ) {
            @Override
            public String preparePrompt() {
                return "";
            }

            @Override
            public String selectField() {
                return "";
            }
        };
    }
}