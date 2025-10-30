package com.evalkit.framework.eval.node.counter;

import com.evalkit.framework.eval.model.CountResult;
import com.evalkit.framework.eval.model.DataItem;

import java.util.List;

class CounterTest {
    void test() {
        Counter counter = new Counter() {
            @Override
            protected CountResult count(List<DataItem> dataItems) {
                return null;
            }
        };
    }
}