package com.evalkit.framework.eval.node.dataloader.datagen.querygen.config;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class MockerQueryGeneratorConfig extends QueryGeneratorConfig {
    /* mock失败时填充空值,默认false */
    @Builder.Default
    private boolean fillEmptyStringOnMockFail = false;
}
