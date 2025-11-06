package com.evalkit.framework.eval.mock.engine;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 抽象的mock规则引擎
 */
public abstract class AbstractMockRuleEngine implements MockRuleEngine {
    /**
     * 根据规则生成mock结果
     *
     * @param rule 规则
     * @return mock结果
     */
    @Override
    public String mock(String rule) {
        String[] parts = rule.split("\\s+", 2);
        String ruleName = parts[0];
        List<String> ruleParams = parts.length > 1
                ? Arrays.asList(parts[1].split("\\s+"))
                : Collections.emptyList();
        return mock(rule, ruleName, ruleParams);
    }

    /**
     * 根据规则名称和规则参数生成mock结果
     *
     * @param rawRule    原始规则
     * @param ruleName   解析后的规则名称
     * @param ruleParams 解析后的规则参数
     * @return mock结果
     */
    public abstract String mock(String rawRule, String ruleName, List<String> ruleParams);
}
