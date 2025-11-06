package com.evalkit.framework.eval.node.dataloader.datagen.config;

import com.evalkit.framework.eval.node.dataloader.datagen.querygen.QueryGenerator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 评测用例数据生成器配置
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class EvalCaseDataGeneratorConfig extends DataGeneratorConfig {
    /* 评测用例数量, 默认1 */
    @Builder.Default
    protected int genCount = 1;
    /* 轮次数量 */
    @Builder.Default
    protected int roundCount = 1;
    /* 随机轮次 */
    @Builder.Default
    protected boolean randomRound = false;
    /* Query生成器 */
    protected QueryGenerator queryGenerator;
    /* 并发生成数量 */
    @Builder.Default
    protected int threadNum = 1;
    /* 默认字段值 */
    @Builder.Default
    protected String sessionFieldKey = "sessionId";
    @Builder.Default
    protected String roundFieldKey = "round";
    @Builder.Default
    protected String queryFieldKey = "query";
    @Builder.Default
    protected String groundTruthFieldKey = "groundTruth";
    @Builder.Default
    protected String intentFieldKey = "intent";
    @Builder.Default
    protected String contextDependencyFieldKey = "contextDependency";
}
