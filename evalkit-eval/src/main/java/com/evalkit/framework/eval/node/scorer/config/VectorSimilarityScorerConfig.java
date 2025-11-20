package com.evalkit.framework.eval.node.scorer.config;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class VectorSimilarityScorerConfig extends ScorerConfig {
    /* 向量相似度阈值 */
    @Builder.Default
    protected double similarityThreshold = 0.0;
}
