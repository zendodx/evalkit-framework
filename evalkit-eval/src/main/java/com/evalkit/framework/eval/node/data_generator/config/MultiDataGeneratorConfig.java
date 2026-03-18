package com.evalkit.framework.eval.node.data_generator.config;

import com.evalkit.framework.eval.node.data_generator.DataGenerator;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.List;


@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class MultiDataGeneratorConfig extends DataGeneratorConfig {
    protected List<DataGenerator> dataGenerators;
}
