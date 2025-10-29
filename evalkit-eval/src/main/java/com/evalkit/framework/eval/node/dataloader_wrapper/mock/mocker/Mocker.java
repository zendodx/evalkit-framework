package com.evalkit.framework.eval.node.dataloader_wrapper.mock.mocker;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 数据Mock
 */
public interface Mocker {
    /**
     * 判断是否支持mock
     *
     * @param ruleName   规则名称
     * @param ruleParams 规则参数
     * @return 是否支持mock
     */
    boolean support(String ruleName, List<String> ruleParams);

    /**
     * mock数据
     *
     * @param ruleName   规则名称
     * @param ruleParams 规则参数
     * @return mock数据
     */
    String mock(String ruleName, List<String> ruleParams);

    /**
     * 随机选择
     *
     * @param list 待选择数据集合
     * @param <T>  数据类型
     * @return 随机选择的数据
     */
    static <T> T randomChoose(List<? extends T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        int idx = ThreadLocalRandom.current().nextInt(list.size());
        return list.get(idx);
    }
}