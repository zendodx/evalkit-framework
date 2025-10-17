package com.evalkit.framework.eval.node.dataloader_wrapper.mock;


import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class AbstractMockRuleEngine implements MockRuleEngine {
    protected String rule;

    /* 解析规则名称和规则参数 */
    @Override
    public String mock(String rule) {
        this.rule = rule;
        String[] parts = rule.split("\\s+", 2);
        String ruleName = parts[0];
        List<String> ruleParams = parts.length > 1
                ? Arrays.asList(parts[1].split("\\s+"))
                : Collections.emptyList();
        return mock(ruleName, ruleParams);
    }

    public abstract String mock(String ruleName, List<String> ruleParams);
}
