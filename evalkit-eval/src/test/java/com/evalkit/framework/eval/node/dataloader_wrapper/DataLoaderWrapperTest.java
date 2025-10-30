package com.evalkit.framework.eval.node.dataloader_wrapper;

import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.node.dataloader_wrapper.config.DataLoaderWrapperConfig;

class DataLoaderWrapperTest {
    void test() {
        DataLoaderWrapper dataLoaderWrapper = new DataLoaderWrapper(
                DataLoaderWrapperConfig.builder().build()
        ) {
            @Override
            protected void wrapper(DataItem dataItem) {
                // 增强dataItem
            }
        };
    }
}