package com.evalkit.framework.eval.node.scorer.checker.model;

import org.junit.jupiter.api.Test;

class CheckItemTest {
    void test() {
        CheckItem.builder()
                .name("检查项名称")
                .checkDescription("检查描述")
                .star(true)
                .build();
    }
}