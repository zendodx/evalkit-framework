package com.evalkit.framework.eval.node.dataloader_wrapper;

import com.evalkit.framework.eval.node.dataloader_wrapper.config.PolishDataLoaderWrapperConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 文本润色数据装饰器,使用大模型对文本进行润色
 */
@Slf4j
public abstract class PolishDataLoaderWrapper extends PromptDataLoaderWrapper {
    protected PolishDataLoaderWrapperConfig config;

    public PolishDataLoaderWrapper(PolishDataLoaderWrapperConfig config) {
        super(config.getThreadNum(), config.getLlmService());
        this.config = config;
    }

    /**
     * 构造润色的prompt
     */
    @Override
    public String preparePrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append(config.getSysPrompt());
        sb.append("\n");
        sb.append(String.format("【要求1】语言风格保持:%s", config.getStyle()));
        String splitChar = config.getSplitChar();
        if (StringUtils.isNotEmpty(splitChar)) {
            sb.append("\n");
            sb.append(String.format("【要求2】'%s'是句子分隔符, 要润色 '%s' 之间的每个字句, 优化后的句子必须保留 '%s' 且不能增删 '%s' 数量", splitChar, splitChar, splitChar, splitChar));
        }
        return sb.toString();
    }

    @Override
    public abstract String selectField();
}
