package com.evalkit.framework.eval.node.scorer.model;

import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.node.scorer.Scorer;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.util.function.Function;

/**
 * 评估器路由规则（方案B：RouterScorer 使用）。
 *
 * <p>每条路由规则由一个 <b>匹配条件</b> 和一个 <b>目标评估器</b> 组成。
 * RouterScorer 会按路由列表的顺序依次尝试匹配，
 * 将 {@link DataItem} 委托给第一个（或全部）满足条件的目标 Scorer 执行。</p>
 *
 * <p>快速创建示例：</p>
 * <pre>{@code
 * ScorerRoute.of(
 *     item -> "chat".equals(item.getInputData().get("scene")),
 *     myChatScorer,
 *     "对话场景"
 * )
 * }</pre>
 */
@Data
@Builder
public class ScorerRoute {

    /**
     * 路由名称，用于日志和报告展示，建议填写可读的场景名称。
     */
    @NonNull
    private String routeName;

    /**
     * 路由匹配条件：对 {@link DataItem} 返回 {@code true} 时命中本路由。
     */
    @NonNull
    private Function<DataItem, Boolean> condition;

    /**
     * 命中本路由后委托执行的目标 Scorer。
     */
    @NonNull
    private Scorer scorer;

    /**
     * 快捷工厂方法。
     *
     * @param condition 匹配条件
     * @param scorer    目标评估器
     * @param routeName 路由名称（用于日志）
     * @return 路由规则
     */
    public static ScorerRoute of(Function<DataItem, Boolean> condition, Scorer scorer, String routeName) {
        return ScorerRoute.builder()
                .condition(condition)
                .scorer(scorer)
                .routeName(routeName)
                .build();
    }

    /**
     * 判断当前 {@link DataItem} 是否命中本路由。
     *
     * @param dataItem 待路由的数据项
     * @return {@code true} 命中；{@code false} 不命中
     */
    public boolean matches(DataItem dataItem) {
        return Boolean.TRUE.equals(condition.apply(dataItem));
    }
}

