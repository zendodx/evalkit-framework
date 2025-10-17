package com.evalkit.framework.eval.node.dataloader_wrapper.mock.mocker;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public interface Mocker {
    boolean support(String ruleName, List<String> ruleParams);

    String mock(String ruleName, List<String> ruleParams);

    static <T> T randomChoose(List<? extends T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        int idx = ThreadLocalRandom.current().nextInt(list.size());
        return list.get(idx);
    }
}