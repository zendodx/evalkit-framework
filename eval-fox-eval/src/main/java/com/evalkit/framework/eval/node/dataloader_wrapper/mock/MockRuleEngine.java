package com.evalkit.framework.eval.node.dataloader_wrapper.mock;


import com.evalkit.framework.eval.node.dataloader_wrapper.mock.mocker.Mocker;

import java.util.List;

/**
 * mock规则引擎
 */
public interface MockRuleEngine {
    String mock(String rule);

    void addMocker(Mocker mocker);

    void addMockers(List<Mocker> mockers);

    void addMockers(Mocker... mockers);
}
