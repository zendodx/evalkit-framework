package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.scorer.config.RouterScorerConfig;
import com.evalkit.framework.eval.node.scorer.model.ScorerRoute;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 路由评估器
 *
 * <p>根据每个 {@link DataItem} 的字段值，将其动态路由到对应的 {@link Scorer} 执行评估。
 * 适用于同一数据集包含多个场景、不同场景需要使用不同评估器的情况。</p>
 *
 * <h3>路由匹配模式</h3>
 * <ul>
 *   <li><b>first-match（默认）</b>：每个 DataItem 只由第一个命中的 {@link ScorerRoute} 处理，
 *       结果只有一条 ScorerResult。</li>
 *   <li><b>match-all</b>：每个 DataItem 由所有命中的路由依次处理，
 *       但 RouterScorer 的 {@link #eval} 只返回一条汇总 ScorerResult；
 *       如需多条独立结果，建议在 Workflow 中直接串联多个带 {@code condition} 的 Scorer（方案A）。</li>
 * </ul>
 *
 * <h3>无路由命中时的行为</h3>
 * <ul>
 *   <li>若 {@link RouterScorerConfig#getDefaultScorer()} 不为 {@code null}，则委托兜底评估器处理。</li>
 *   <li>否则返回跳过结果（score = skipScore，totalScore = 0，不计入汇总）。</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * RouterScorer router = new RouterScorer(
 *     RouterScorerConfig.builder()
 *         .metricName("场景路由评估")
 *         .routes(Arrays.asList(
 *             ScorerRoute.of(item -> "chat".equals(item.getInputData().get("scene")),   chatScorer,   "对话场景"),
 *             ScorerRoute.of(item -> "search".equals(item.getInputData().get("scene")), searchScorer, "搜索场景"),
 *             ScorerRoute.of(item -> "rag".equals(item.getInputData().get("scene")),    ragScorer,    "RAG场景")
 *         ))
 *         .defaultScorer(fallbackScorer)
 *         .build()
 * );
 *
 * Workflow evalWorkflow = Workflow.builder().addNode(router).build();
 * }</pre>
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class RouterScorer extends Scorer {

    protected final RouterScorerConfig routerConfig;

    public RouterScorer(RouterScorerConfig config) {
        super(config);
        validRouterConfig(config);
        this.routerConfig = config;
        super.scorerType = "routerScorer";
    }

    private void validRouterConfig(RouterScorerConfig config) {
        if (CollectionUtils.isEmpty(config.getRoutes())) {
            throw new IllegalArgumentException("RouterScorerConfig.routes must not be empty");
        }
    }

    /**
     * 根据路由规则将 DataItem 分发到对应的 Scorer 执行评估。
     *
     * <p>在执行子 Scorer 的 {@code eval()} 之前，会将本 RouterScorer 的 workflowContext
     * 注入到子 Scorer 中，确保子 Scorer 可以访问上下文（ScoreStrategy、Threshold 等）。</p>
     *
     * @param dataItem 待评估数据项
     * @return 评估结果；若 matchAll=false 则为第一个命中路由的结果；
     *         若 matchAll=true 则为所有命中路由结果的合并（取平均分和拼接理由）；
     *         无命中时为跳过结果
     * @throws Exception 子 Scorer 评估抛出的异常
     */
    @Override
    public ScorerResult eval(DataItem dataItem) throws Exception {
        List<ScorerRoute> routes = routerConfig.getRoutes();

        if (routerConfig.isMatchAll()) {
            // match-all 模式：收集所有命中路由的结果
            List<ScorerResult> matched = new ArrayList<>();
            for (ScorerRoute route : routes) {
                if (route.matches(dataItem)) {
                    log.debug("RouterScorer match-all hit route [{}] for dataIndex={}", route.getRouteName(), dataItem.getDataIndex());
                    matched.add(delegateEval(route.getScorer(), dataItem));
                }
            }
            if (!matched.isEmpty()) {
                return mergeResults(matched, dataItem);
            }
        } else {
            // first-match 模式：找到第一个命中的路由
            for (ScorerRoute route : routes) {
                if (route.matches(dataItem)) {
                    log.debug("RouterScorer first-match hit route [{}] for dataIndex={}", route.getRouteName(), dataItem.getDataIndex());
                    return delegateEval(route.getScorer(), dataItem);
                }
            }
        }

        // 无命中，尝试兜底评估器
        Scorer defaultScorer = routerConfig.getDefaultScorer();
        if (defaultScorer != null) {
            log.debug("RouterScorer no route matched, using defaultScorer for dataIndex={}", dataItem.getDataIndex());
            return delegateEval(defaultScorer, dataItem);
        }

        // 无命中且无兜底，返回跳过结果
        log.debug("RouterScorer no route matched and no defaultScorer, skipping dataIndex={}", dataItem.getDataIndex());
        return buildSkipResult(dataItem);
    }

    /**
     * 将 workflowContext 注入子 Scorer，然后委托其执行 {@code eval()}。
     *
     * @param scorer   目标评估器
     * @param dataItem 待评估数据项
     * @return 子 Scorer 的评估结果（原始，未经 doEval 包装）
     * @throws Exception 子 Scorer 抛出的异常
     */
    private ScorerResult delegateEval(Scorer scorer, DataItem dataItem) throws Exception {
        scorer.setWorkflowContext(this.getWorkflowContext());
        return scorer.eval(dataItem);
    }

    /**
     * match-all 模式下合并多条路由结果：取平均分、拼接理由。
     *
     * @param results  各路由结果列表（非空）
     * @param dataItem 原始数据项（用于填充 dataIndex）
     * @return 合并后的 ScorerResult
     */
    private ScorerResult mergeResults(List<ScorerResult> results, DataItem dataItem) {
        double avgScore = results.stream().mapToDouble(ScorerResult::getScore).average().orElse(0);
        double avgTotalScore = results.stream().mapToDouble(ScorerResult::getTotalScore).average().orElse(0);
        String reason = results.stream()
                .map(r -> "[" + r.getMetric() + "] " + r.getReason())
                .collect(Collectors.joining(" | "));
        return new ScorerResult(routerConfig.getMetricName(), avgScore, avgTotalScore, reason);
    }
}

