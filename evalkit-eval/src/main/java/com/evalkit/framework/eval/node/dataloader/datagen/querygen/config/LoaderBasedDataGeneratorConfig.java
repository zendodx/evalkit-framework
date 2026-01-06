package com.evalkit.framework.eval.node.dataloader.datagen.querygen.config;

import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.eval.node.dataloader.datagen.config.DataGeneratorConfig;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
public class LoaderBasedDataGeneratorConfig extends DataGeneratorConfig {
    private DataLoader dataLoader;
    @Builder.Default
    private Integer threadNum = 1;
}
