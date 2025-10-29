package com.evalkit.framework.eval.node.scorer.checker.config;

import com.evalkit.framework.eval.node.scorer.checker.model.CheckItem;
import com.evalkit.framework.eval.node.scorer.checker.strategy.checkitem.CheckItemScoreMergeStrategy;
import com.evalkit.framework.eval.node.scorer.checker.strategy.checkitem.SumCheckItemScoreMergeStrategy;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Data
@SuperBuilder
public class CheckerConfig {
    /* 检查器名称 */
    @Builder.Default
    private String name = "未命名检查";
    /* 检查项列表 */
    @Builder.Default
    private List<CheckItem> checkItems = new ArrayList<>();
    /* 合并各检查项后的最终检查分数 */
    @Builder.Default
    private double score = 0.0;
    /* 检查器总分 */
    @Builder.Default
    private double totalScore = 0.0;
    /* 合并各检查项后的检查理由 */
    @Builder.Default
    private String reason = "";
    /*  检查项分数合并策略,默认是求和 */
    @Builder.Default
    private CheckItemScoreMergeStrategy strategy = new SumCheckItemScoreMergeStrategy();
    /* 检查通过阈值 */
    @Builder.Default
    private double threshold = 0.0;
    /* 是否为必过checker */
    @Builder.Default
    private boolean star = false;
}
