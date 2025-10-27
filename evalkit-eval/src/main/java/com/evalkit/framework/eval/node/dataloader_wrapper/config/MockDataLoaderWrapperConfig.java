package com.evalkit.framework.eval.node.dataloader_wrapper.config;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
public class MockDataLoaderWrapperConfig extends DataLoaderWrapperConfig {
    /* 不同字段的相同标记符mock为同一个值,默认false */
    @Builder.Default
    private boolean sameMock = false;
    /* mock失败时填充空值,默认false */
    @Builder.Default
    private boolean fillEmptyStringOnMockFail = false;
}
