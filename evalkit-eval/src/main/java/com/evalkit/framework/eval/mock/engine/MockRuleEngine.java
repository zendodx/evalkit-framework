package com.evalkit.framework.eval.mock.engine;


import com.evalkit.framework.eval.mock.mocker.Mocker;

import java.util.List;
import java.util.regex.Matcher;

/**
 * mock规则引擎
 */
public interface MockRuleEngine {
    /**
     * 根据规则生成mock结果
     *
     * @param rule mock规则
     * @return mock结果
     */
    String mock(String rule);

    /**
     * 添加mock器
     *
     * @param mocker mock器
     */
    void addMocker(Mocker mocker);

    /**
     * 批量添加mock器
     *
     * @param mockers mock器列表
     */
    void addMockers(List<Mocker> mockers);

    /**
     * 批量添加mock器
     *
     * @param mockers mock器数组
     */
    void addMockers(Mocker... mockers);

    /**
     * 获取给定文本中的所有规则
     *
     * @param text 给定文本
     * @return 规则列表
     */
    List<String> matchRules(String text);

    /**
     * 获取规则匹配器
     *
     * @param text 待匹配文本
     * @return 规则匹配器
     */
    Matcher getMatcher(String text);
}
