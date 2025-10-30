package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.node.scorer.config.ScorerConfig;
import org.apache.commons.lang3.tuple.Pair;

class VectorSimilarityScorerTest {
    void test() {
        VectorSimilarityScorer vectorSimilarityScorer = new VectorSimilarityScorer(
                ScorerConfig.builder().build(),
                0.8
        ) {
            @Override
            public Pair<String, String> prepareFieldPair(DataItem dataItem) {
                return null;
            }
        };
    }
}