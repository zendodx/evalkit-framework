package com.evalkit.framework.eval.model;

import com.evalkit.framework.eval.node.scorer.strategy.EvalReasonStrategy;
import com.evalkit.framework.eval.node.scorer.strategy.NormalEvalReasonStrategy;
import com.evalkit.framework.eval.node.scorer.strategy.ScoreStrategy;
import com.evalkit.framework.eval.node.scorer.strategy.SumScoreStrategy;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 评测最终结果,汇总多个评估器的结果
 */
@Data
@Builder
@AllArgsConstructor
public class EvalResult {
    /* 默认评估分数计算策略为求和 */
    protected final static ScoreStrategy DEFAULT_SCORE_STRATEGY = new SumScoreStrategy();
    /* 默认评估分数通过阈值 */
    protected final static double DEFAULT_SCORE_THRESHOLD = 0;
    /* 默认评估理由构建策略 */
    protected final static EvalReasonStrategy DEFAULT_EVAL_REASON_STRATEGY = new NormalEvalReasonStrategy();
    /* 数据索引 */
    protected Long dataIndex;
    /* 评测分数 */
    protected double score;
    /* 评测理由,jsonArray格式,可用于归因 */
    protected String reason;
    /* 评测开始时间戳 */
    protected long startTime;
    /* 评测结束时间戳 */
    protected long endTime;
    /* 评测耗时 */
    protected long timeCost;
    /* 评估器结果列表 */
    protected List<ScorerResult> scorerResults;
    /* 评测是否成功 */
    protected boolean success;
    /* 评测是否通过 */
    protected boolean pass;
    /* 评测通过阈值 */
    protected double threshold;
    /* 评估器分数整合策略 */
    @JsonIgnore
    protected ScoreStrategy scoreStrategy;
    protected String scoreStrategyName;
    /* 评估器评测理由构建策略 */
    @JsonIgnore
    protected EvalReasonStrategy evalReasonStrategy;
    protected String evalReasonStrategyName;

    public EvalResult() {
        this(new CopyOnWriteArrayList<>(), DEFAULT_SCORE_STRATEGY, DEFAULT_SCORE_THRESHOLD, DEFAULT_EVAL_REASON_STRATEGY);
    }

    public EvalResult(List<ScorerResult> scorerResults) {
        this(scorerResults, DEFAULT_SCORE_STRATEGY, DEFAULT_SCORE_THRESHOLD, DEFAULT_EVAL_REASON_STRATEGY);
    }

    public EvalResult(List<ScorerResult> scorerResults, ScoreStrategy scoreStrategy, double threshold, EvalReasonStrategy evalReasonStrategy) {
        this.score = 0;
        this.reason = "";
        this.scorerResults = scorerResults;
        this.scoreStrategy = scoreStrategy;
        this.scoreStrategyName = scoreStrategy.getStrategyName();
        this.threshold = threshold;
        this.evalReasonStrategy = evalReasonStrategy;
    }

    /**
     * 添加评估器结果,同时更新评测结果
     */
    public synchronized void addScorerResult(ScorerResult scorerResult) {
        if (scorerResults == null) {
            scorerResults = new CopyOnWriteArrayList<>();
        }
        this.scorerResults.add(scorerResult);
        this.updateEvalResult();
    }

    /**
     * 更新评测结果
     */
    public void updateEvalResult() {
        if (CollectionUtils.isEmpty(scorerResults)) {
            return;
        }
        updateScore();
        updateReason();
        updateTimeCost();
        updatePass();
    }

    private void updatePass() {
        // 先检查是否存在必过评估器没过的情况
        for (ScorerResult scorerResult : scorerResults) {
            boolean pass = scorerResult.isPass();
            boolean star = scorerResult.isStar();
            if (star && !pass) {
                this.pass = false;
                return;
            }
        }
        // 如果必过评估器都通过,则看最终的分数是否大于等于阈值
        pass = this.score >= threshold;
    }

    /**
     * 计算评测耗时和评测状态
     */
    private void updateTimeCost() {
        List<ScorerResult> successScorerResults = scorerResults.stream().filter(ScorerResult::isSuccess).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(successScorerResults) && successScorerResults.size() == scorerResults.size()) {
            this.success = true;
            List<Long> startTimes = successScorerResults.stream().map(ScorerResult::getStatTime).collect(Collectors.toList());
            List<Long> endTimes = successScorerResults.stream().map(ScorerResult::getEndTime).collect(Collectors.toList());
            List<Long> timeCosts = successScorerResults.stream().map(ScorerResult::getTimeCost).collect(Collectors.toList());
            this.startTime = startTimes.stream().min(Long::compareTo).orElse(0L);
            this.endTime = endTimes.stream().max(Long::compareTo).orElse(0L);
            this.timeCost = timeCosts.stream().max(Long::compareTo).orElse(0L);
        }
    }

    /**
     * 更新评测分数,更新策略有以下几种:
     * 1.最小分数策略: 取各评估器的最小分数
     * 2.平均分数策略: 计算各评估器分数的平均值
     * 3.分数求和策略: 计算各评估器分数之和
     * 4.平均得分率策略: 计算各评估器得分率平均值
     * 5.得分率求和策略: 计算各评估器得分率之和
     * 默认使用分数求和策略
     */
    private void updateScore() {
        this.score = scoreStrategy.calScore(scorerResults);
    }

    /**
     * 更新评测理由,汇总各评估器的指标和理由
     */
    private void updateReason() {
        this.reason = evalReasonStrategy.buildEvalReason(scorerResults);
    }
}
