package com.evalkit.framework.eval.node.dataloader.config;

import com.evalkit.framework.eval.model.InputData;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 数据加载配置类
 */
@SuperBuilder
@Data
public class DataLoaderConfig {
    // 偏移量,默认值0
    @Builder.Default
    protected int offset = 0;
    // 页数,默认值-1,加载所有
    @Builder.Default
    protected int limit = -1;
    // 过滤器
    @Builder.Default
    protected List<Predicate<InputData>> filters = new ArrayList<>();
    // 是否打乱顺序
    @Builder.Default
    protected boolean shuffle = false;
    /* 是否开启注入数据 */
    @Builder.Default
    protected boolean openInjectData = false;
    /* 如果开启注入数据,则默认注入所有 */
    @Builder.Default
    protected boolean injectDataIndex = true;
    @Builder.Default
    protected boolean injectInputData = true;
    @Builder.Default
    protected boolean injectApiCompletionResult = true;
    @Builder.Default
    protected boolean injectEvalResult = true;
    @Builder.Default
    protected boolean injectExtra = true;
}
