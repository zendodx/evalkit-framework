package com.evalkit.framework.eval.node.data_generator.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TestCase {
    public String testCaseId;
    public String scenarioId;
    public String sessionId;
    public List<Turn> queries;
    public Map<String, String> kgSource;
}
