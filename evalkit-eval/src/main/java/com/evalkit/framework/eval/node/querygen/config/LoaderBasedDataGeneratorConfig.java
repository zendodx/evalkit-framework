package com.evalkit.framework.eval.node.querygen.config;

import com.evalkit.framework.eval.node.data_generator.config.DataGeneratorConfig;
import com.evalkit.framework.eval.node.dataloader.DataLoader;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
public class LoaderBasedDataGeneratorConfig extends DataGeneratorConfig {
    private DataLoader dataLoader;
}
