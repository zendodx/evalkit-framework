package com.evalkit.framework.eval.node.data_generator.model;

import lombok.Data;

@Data
public class ScenarioConfig {
    public String scenarioId;
    public String sparqlTemplate;
    public double minSimilarity;
    public double maxSimilarity;
    public GoldenCase goldenCase;
}
