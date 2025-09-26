package com.evalkit.framework.eval.node.dataloader_wrapper.mock;

import com.evalkit.framework.eval.node.dataloader_wrapper.mock.mocker.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于表达式的Mock规则引擎
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class SpelMockRuleEngine extends AbstractMockRuleEngine {
    private List<Mocker> mockers;
    /* mock失败时是否使用空字符替换 */
    private boolean fillEmptyStringOnMockFail;

    public SpelMockRuleEngine() {
        this(false);
    }

    public SpelMockRuleEngine(boolean fillEmptyStringOnMockFail) {
        this.fillEmptyStringOnMockFail = fillEmptyStringOnMockFail;
        this.mockers = new ArrayList<>();
        this.mockers.add(new HolidayMocker());
        this.mockers.add(new DateMocker());
        this.mockers.add(new ChinaAddressMocker());
        this.mockers.add(new ChinaPOIMocker());
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
    public String mock(String ruleName, List<String> ruleParams) {
        String mockedText = null;
        for (Mocker mocker : mockers) {
            if (mocker.support(ruleName, ruleParams)) {
                mockedText = mocker.mock(ruleName, ruleParams);
            }
        }
        if (mockedText == null) {
            return fillEmptyStringOnMockFail ? "" : String.format("{{%s}}", rule);
        }
        return mockedText;
    }
}
