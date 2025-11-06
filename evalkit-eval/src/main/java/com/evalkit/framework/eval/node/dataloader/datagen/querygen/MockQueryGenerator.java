package com.evalkit.framework.eval.node.dataloader.datagen.querygen;

import com.evalkit.framework.eval.mock.engine.MockRuleEngine;
import com.evalkit.framework.eval.mock.engine.SpelMockRuleEngine;
import com.evalkit.framework.eval.node.dataloader.datagen.querygen.config.MockerQueryGeneratorConfig;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Mock Query生成器
 */
@Getter
public abstract class MockQueryGenerator implements QueryGenerator {
    /* mock规则引擎 */
    protected final MockRuleEngine engine;
    /* Mock配置 */
    protected final MockerQueryGeneratorConfig config;

    public MockQueryGenerator() {
        this(MockerQueryGeneratorConfig.builder().build());
    }

    public MockQueryGenerator(MockerQueryGeneratorConfig config) {
        this.config = config;
        this.engine = new SpelMockRuleEngine(config.isFillEmptyStringOnMockFail());
    }

    /**
     * 准备模板Query
     *
     * @return 模板Query
     */
    public abstract String prepareTemplateQuery();

    @Override
    public List<String> generate() {
        String templateQuery = prepareTemplateQuery();
        if (StringUtils.isEmpty(templateQuery)) {
            return null;
        }
        int genCount = config.getGenCount();
        List<String> queries = new ArrayList<>(genCount);
        for (int i = 0; i < genCount; i++) {
            queries.add(mock(templateQuery));
        }
        return queries;
    }

    /**
     * 解析文本中的mock规则,生成mock数据,填充mock数据返回
     */
    private String mock(String text) {
        Matcher matcher = engine.getMatcher(text);
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
