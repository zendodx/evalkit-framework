package com.evalkit.framework.eval.node.scorer.strategy;

import com.evalkit.framework.eval.model.ScorerResult;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * 评测原因构建策略
 */
public interface EvalReasonStrategy {
    /**
     * 构建评测原因
     *
     * @param scorerResults 评估器结果集合
     * @return 评测原因
     */
    String buildEvalReason(List<ScorerResult> scorerResults);

    /**
     * 获取构建评测原因策略的名称
     *
     * @return 策略名称
     */
    String getStrategyName();

    /**
     * 拼接原始的评估器理由
     *
     * @param scorerResults 评估器结果集合
     * @return 原始评估理由
     */
    default String appendRawReason(List<ScorerResult> scorerResults) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (ScorerResult scorerResult : scorerResults) {
            String curReason = scorerResult.getReason();
            if (StringUtils.isEmpty(curReason)) {
                continue;
            }
            sb.append(index).append(". ").append(curReason).append("\n");
            index++;
        }
        return sb.toString();
    }
}
