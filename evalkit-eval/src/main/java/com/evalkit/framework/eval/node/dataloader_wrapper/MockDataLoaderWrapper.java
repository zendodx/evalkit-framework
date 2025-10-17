package com.evalkit.framework.eval.node.dataloader_wrapper;

import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.dataloader_wrapper.mock.MockRuleEngine;
import com.evalkit.framework.eval.node.dataloader_wrapper.mock.SpelMockRuleEngine;
import com.evalkit.framework.eval.node.dataloader_wrapper.mock.mocker.Mocker;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Mock数据装饰器
 */
@Slf4j
public abstract class MockDataLoaderWrapper extends DataLoaderWrapper {
    /* 匹配规则 {{rule}} */
    protected static final Pattern RULE_PATTERN = Pattern.compile("\\{\\{([^{}]*)}}");
    /* mock规则引擎 */
    protected final MockRuleEngine engine;
    /* 各字段相同标记是否mock为统一字符 */
    protected boolean sameMock;


    public MockDataLoaderWrapper() {
        this(false, false);
    }

    public MockDataLoaderWrapper(boolean sameMock, boolean fillEmptyStringOnMockFail) {
        this.sameMock = sameMock;
        // 默认仅支持基于表达式的规则引擎
        engine = new SpelMockRuleEngine(fillEmptyStringOnMockFail);
    }

    /**
     * 引擎添加mocker
     */
    public void addMocker(Mocker mocker) {
        if (mocker == null) {
            return;
        }
        engine.addMocker(mocker);
    }

    public void addMockers(List<Mocker> mockers) {
        engine.addMockers(mockers.stream().filter(Objects::isNull).collect(Collectors.toList()));
    }

    public void addMockers(Mocker... mockers) {
        engine.addMockers(Arrays.stream(mockers).filter(Objects::nonNull).collect(Collectors.toList()));
    }

    /**
     * 选择要mock的字段
     */
    public abstract List<String> selectMockFields();

    @Override
    protected void wrapper(DataItem dataItem) {
        List<String> fields = selectMockFields();
        if (CollectionUtils.isEmpty(fields)) {
            return;
        }
        InputData inputData = dataItem.getInputData();
        if (sameMock) {
            mockSameValue(inputData, fields);
        } else {
            mockRandomValue(inputData, fields);
        }
        log.info("Finish wrapper dataItem: {}", dataItem);
    }

    /**
     * 各字段相同标记mock相同值
     */
    protected void mockSameValue(InputData inputData, List<String> fields) {
        // 构建规则与字段集合映射,统计rule存在哪个字段中,后续统一Mock
        Map<String, List<String>> ruleFieldMap = new HashMap<>();
        for (String field : fields) {
            String fieldValue = inputData.get(field, null);
            if (StringUtils.isEmpty(fieldValue)) {
                continue;
            }
            List<String> rules = matchRules(fieldValue);
            for (String rule : rules) {
                if (StringUtils.isNotEmpty(rule)) {
                    if (!ruleFieldMap.containsKey(rule)) {
                        ruleFieldMap.put(rule, new ArrayList<>());
                    }
                    ruleFieldMap.get(rule).add(field);
                }
            }
        }
        // 按映射表执行mock
        for (Map.Entry<String, List<String>> entry : ruleFieldMap.entrySet()) {
            String rule = entry.getKey();
            String mockedValue = engine.mock(rule);
            List<String> ruleFields = entry.getValue();
            for (String ruleField : ruleFields) {
                String ruleFieldValue = inputData.get(ruleField);
                String replace = StringUtils.replace(ruleFieldValue, String.format("{{%s}}", rule), mockedValue);
                inputData.set(ruleField, replace);
            }
        }
    }

    /**
     * 获取文本中的标记值
     */
    protected List<String> matchRules(String text) {
        if (StringUtils.isEmpty(text)) {
            return null;
        }
        List<String> rules = new ArrayList<>();
        Matcher matcher = RULE_PATTERN.matcher(text);
        while (matcher.find()) {
            rules.add(matcher.group(1).trim());
        }
        return rules;
    }

    /**
     * 各字段相同标记mock随机值
     */
    protected void mockRandomValue(InputData inputData, List<String> fields) {
        for (String field : fields) {
            String fieldValue = inputData.get(field);
            if (StringUtils.isEmpty(fieldValue)) {
                continue;
            }
            String mockedFieldValue = mock(fieldValue);
            inputData.set(field, mockedFieldValue);
        }
    }

    /**
     * 解析文本中的mock规则,生成mock数据,填充mock数据返回
     */
    private String mock(String text) {
        Matcher matcher = RULE_PATTERN.matcher(text);
        // 没有找到匹配项返回原文本
        if (!matcher.find()) {
            return text;
        }
        StringBuffer sb = new StringBuffer();
        do {
            String rule = matcher.group(1).trim();
            String mockedValue = engine.mock(rule);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(mockedValue));
        } while (matcher.find());
        matcher.appendTail(sb);
        return sb.toString();
    }
}
