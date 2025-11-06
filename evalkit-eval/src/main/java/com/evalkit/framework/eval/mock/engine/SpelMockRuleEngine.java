package com.evalkit.framework.eval.mock.engine;

import com.evalkit.framework.eval.mock.mocker.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 基于表达式的Mock规则引擎
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class SpelMockRuleEngine extends AbstractMockRuleEngine {
    /* 表达式Mock规则引擎支持的匹配规则为 {{rule}} */
    protected static final Pattern RULE_PATTERN = Pattern.compile("\\{\\{([^{}]*)}}");
    /* 要使用的生成器集合 */
    private List<Mocker> mockers;
    /* mock失败时是否使用空字符替换 */
    private boolean fillEmptyStringOnMockFail;

    public SpelMockRuleEngine() {
        this(false);
    }

    public SpelMockRuleEngine(boolean fillEmptyStringOnMockFail) {
        this.fillEmptyStringOnMockFail = fillEmptyStringOnMockFail;
        this.mockers = new ArrayList<>();
        this.mockers.add(new ChinaHolidayMocker());
        this.mockers.add(new DateMocker());
        this.mockers.add(new ChinaAddressMocker());
        this.mockers.add(new ChinaPoiMocker());
    }

    public void addMocker(Mocker mocker) {
        if (this.mockers == null) {
            this.mockers = new ArrayList<>();
        }
        this.mockers.add(mocker);
    }

    public void addMockers(List<Mocker> mockers) {
        if (this.mockers == null) {
            this.mockers = new ArrayList<>();
        }
        this.mockers.addAll(mockers);
    }

    @Override
    public void addMockers(Mocker... mockers) {
        this.addMockers(Arrays.stream(mockers).collect(Collectors.toList()));
    }

    @Override
    public String mock(String rawRule, String ruleName, List<String> ruleParams) {
        String mockedText = null;
        for (Mocker mocker : mockers) {
            if (mocker.support(ruleName, ruleParams)) {
                mockedText = mocker.mock(ruleName, ruleParams);
            }
        }
        if (mockedText == null) {
            return fillEmptyStringOnMockFail ? "" : String.format("{{%s}}", rawRule);
        }
        return mockedText;
    }

    /**
     * 获取文本中的标记值
     */
    public List<String> matchRules(String text) {
        if (StringUtils.isEmpty(text)) {
            return null;
        }
        List<String> rules = new ArrayList<>();
        Matcher matcher = getMatcher(text);
        while (matcher.find()) {
            rules.add(matcher.group(1).trim());
        }
        return rules;
    }

    @Override
    public Matcher getMatcher(String text) {
        return RULE_PATTERN.matcher(text);
    }
}
