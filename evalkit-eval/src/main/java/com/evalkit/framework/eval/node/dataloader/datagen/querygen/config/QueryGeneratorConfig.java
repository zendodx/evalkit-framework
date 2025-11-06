package com.evalkit.framework.eval.node.dataloader.datagen.querygen.config;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class QueryGeneratorConfig {
    /* 生成Query数量, 默认 1 */
    @Builder.Default
    protected int genCount = 1;
}
