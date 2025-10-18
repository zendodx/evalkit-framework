package com.evalkit.framework.eval.node.scorer.checker.strategy.checker;


import com.evalkit.framework.eval.node.scorer.checker.Checker;

import java.util.List;

public interface MergeCheckerScoreStrategy {
    String getStrategyName();

    double mergeScore(List<Checker> checkers);
}
