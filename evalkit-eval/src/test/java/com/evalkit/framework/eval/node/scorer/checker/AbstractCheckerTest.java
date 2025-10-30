package com.evalkit.framework.eval.node.scorer.checker;

import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.node.scorer.checker.model.CheckItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AbstractCheckerTest {
    void test() {
        AbstractChecker checker = new AbstractChecker() {
            @Override
            public boolean support(DataItem dataItem) {
                return false;
            }

            @Override
            public double getTotalScore() {
                return 0;
            }

            @Override
            protected List<CheckItem> prepareCheckItems(DataItem dataItem) {
                return null;
            }

            @Override
            protected void check(DataItem dataItem) {

            }
        };
    }
}