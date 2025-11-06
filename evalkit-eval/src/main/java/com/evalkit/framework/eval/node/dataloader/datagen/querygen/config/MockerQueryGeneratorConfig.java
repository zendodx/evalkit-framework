package com.evalkit.framework.eval.node.dataloader.datagen.querygen.config;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class MockerQueryGeneratorConfig {
    /* mock失败时填充空值,默认false */
    @Builder.Default
    private boolean fillEmptyStringOnMockFail = false;
}
