package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.node.scorer.config.VectorSimilarityScorerConfig;
import org.apache.commons.lang3.tuple.Pair;

class VectorSimilarityScorerTest {
    void test() {
        VectorSimilarityScorer vectorSimilarityScorer = new VectorSimilarityScorer(
                VectorSimilarityScorerConfig.builder().similarityThreshold(0.8).build()
        ) {
            @Override
            public Pair<String, String> prepareFieldPair(DataItem dataItem) {
                return null;
            }
        };
    }
}