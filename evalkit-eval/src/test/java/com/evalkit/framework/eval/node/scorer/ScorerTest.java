package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.scorer.config.ScorerConfig;
import org.junit.jupiter.api.Test;

class ScorerTest {
    void test() {
        Scorer scorer = new Scorer(
                ScorerConfig.builder()
                        .metricName("自定义评估器")
                        .threadNum(1)
                        .threshold(0.0)
                        .star(false)
                        .totalScore(1.0)
                        .dynamicTotalScore(false)
                        .build()
        ) {
            @Override
            public ScorerResult eval(DataItem dataItem) {
                ScorerResult result = new ScorerResult("自定义评估器", 1, "理由");
                return result;
            }
        };
    }
}