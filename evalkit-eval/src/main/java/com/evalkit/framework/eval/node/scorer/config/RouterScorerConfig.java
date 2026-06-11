package com.evalkit.framework.eval.node.scorer.config;

import com.evalkit.framework.eval.node.scorer.Scorer;
import com.evalkit.framework.eval.node.scorer.model.ScorerRoute;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 路由评估器配置，通过 ScorerRoute 路由规则将不同场景的 DataItem 分发给对应 Scorer 执行。
 * matchAll=false（默认）first-match；matchAll=true match-all。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class RouterScorerConfig extends ScorerConfig {

    /* 路由规则列表，按顺序匹配，不能为空 */
    private List<ScorerRoute> routes;

    /* 无路由命中时的兜底评估器，null 则返回跳过结果 */
    @Builder.Default
    private Scorer defaultScorer = null;

    /* 路由匹配模式，false=first-match（默认），true=match-all */
    @Builder.Default
    private boolean matchAll = false;
}

