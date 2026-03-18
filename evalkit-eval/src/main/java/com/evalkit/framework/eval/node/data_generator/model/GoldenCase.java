package com.evalkit.framework.eval.node.data_generator.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class GoldenCase {
    public Map<String, String> kgDataUsed;
    public List<Turn> dialogue;
}
