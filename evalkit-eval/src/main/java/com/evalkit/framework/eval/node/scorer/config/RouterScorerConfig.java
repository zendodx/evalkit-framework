package com.evalkit.framework.eval.node.scorer.config;

import com.evalkit.framework.eval.node.scorer.Scorer;
import com.evalkit.framework.eval.node.scorer.model.ScorerRoute;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 路由评估器配置
 *
 * <p>通过一组 {@link ScorerRoute} 路由规则，将不同场景的 {@link com.evalkit.framework.eval.model.DataItem}
 * 动态分发给对应的 {@link Scorer} 执行。适用于同一数据集包含多个场景、不同场景需要使用不同评估器的场景。</p>
 *
 * <h3>路由匹配模式</h3>
 * <ul>
 *   <li><b>first-match（默认）</b>：{@code matchAll = false}，每个 DataItem 只由第一个匹配的路由处理。</li>
 *   <li><b>match-all</b>：{@code matchAll = true}，每个 DataItem 由所有匹配的路由依次处理，
 *       各路由的 ScorerResult 会被分别加入 DataItem 的 EvalResult。</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * RouterScorerConfig config = RouterScorerConfig.builder()
 *     .metricName("场景路由评估")
 *     .routes(Arrays.asList(
 *         ScorerRoute.of(item -> "chat".equals(item.getInputData().get("scene")), chatScorer, "对话场景"),
 *         ScorerRoute.of(item -> "search".equals(item.getInputData().get("scene")), searchScorer, "搜索场景"),
 *         ScorerRoute.of(item -> "rag".equals(item.getInputData().get("scene")), ragScorer, "RAG场景")
 *     ))
 *     .defaultScorer(fallbackScorer)  // 可选，无匹配时的兜底评估器
 *     .matchAll(false)                // 默认 first-match
 *     .build();
 * }</pre>
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class RouterScorerConfig extends ScorerConfig {

    /**
     * 路由规则列表，按顺序匹配。不能为空。
     */
    private List<ScorerRoute> routes;

    /**
     * 无路由命中时的兜底评估器。
     * <p>若为 {@code null}，则无匹配的 DataItem 将生成一条跳过结果（不计入总分）。</p>
     */
    @Builder.Default
    private Scorer defaultScorer = null;

    /**
     * 路由匹配模式：
     * <ul>
     *   <li>{@code false}（默认）：first-match，每个 DataItem 只由第一个命中的路由处理。</li>
     *   <li>{@code true}：match-all，每个 DataItem 由所有命中的路由依次处理。</li>
     * </ul>
     */
    @Builder.Default
    private boolean matchAll = false;
}

