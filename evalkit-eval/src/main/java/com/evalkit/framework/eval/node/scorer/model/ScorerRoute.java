package com.evalkit.framework.eval.node.scorer.model;

import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.node.scorer.Scorer;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.util.function.Function;

/**
 * 评估器路由规则，由匹配条件和目标评估器组成，供 RouterScorer 使用
 */
@Data
@Builder
public class ScorerRoute {

    /* 路由名称，用于日志展示，建议填写可读的场景名称 */
    @NonNull
    private String routeName;

    /* 路由匹配条件，返回 true 时命中本路由 */
    @NonNull
    private Function<DataItem, Boolean> condition;

    /* 命中本路由后委托执行的目标 Scorer */
    @NonNull
    private Scorer scorer;

    /**
     * 快捷工厂方法
     *
     * @param condition 匹配条件
     * @param scorer    目标评估器
     * @param routeName 路由名称
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
     * 判断 DataItem 是否命中本路由
     *
     * @param dataItem 待路由的数据项
     * @return true 命中，false 不命中
     */
    public boolean matches(DataItem dataItem) {
        return Boolean.TRUE.equals(condition.apply(dataItem));
    }
}

