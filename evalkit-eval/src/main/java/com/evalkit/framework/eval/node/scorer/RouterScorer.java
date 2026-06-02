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
 * 路由评估器，根据 DataItem 字段动态路由到对应 Scorer 执行评估。
 * matchAll=false（默认）：first-match，命中第一条路由即执行；
 * matchAll=true：match-all，命中的所有路由依次执行，结果取平均。
 * 无路由命中时若 defaultScorer 不为 null 则委托兜底评估器，否则返回跳过结果。
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
     * 根据路由规则分发 DataItem，执行前将 workflowContext 注入子 Scorer
     *
     * @param dataItem 待评估数据项
     * @return first-match 时第一个命中路由的结果；match-all 时所有命中结果的平均；无命中时跳过结果
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
     * 注入 workflowContext 后委托子 Scorer 执行 eval()
     *
     * @param scorer   目标评估器
     * @param dataItem 待评估数据项
     * @return 子 Scorer 的评估结果
     * @throws Exception 子 Scorer 抛出的异常
     */
    private ScorerResult delegateEval(Scorer scorer, DataItem dataItem) throws Exception {
        scorer.setWorkflowContext(this.getWorkflowContext());
        return scorer.eval(dataItem);
    }

    /**
     * match-all 模式：合并多条路由结果，取平均分、拼接理由
     *
     * @param results  各路由结果列表（非空）
     * @param dataItem 原始数据项
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

