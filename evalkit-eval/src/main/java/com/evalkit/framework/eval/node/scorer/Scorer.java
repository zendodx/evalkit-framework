package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.common.thread.BatchRunner;
import com.evalkit.framework.common.thread.PoolName;
import com.evalkit.framework.eval.constants.NodeNamePrefix;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.scorer.config.ScorerConfig;
import com.evalkit.framework.eval.node.scorer.strategy.ScoreRateStrategy;
import com.evalkit.framework.eval.node.scorer.strategy.ScoreStrategy;
import com.evalkit.framework.eval.node.scorer.strategy.ScoreValueStrategy;
import com.evalkit.framework.workflow.model.WorkflowContext;
import com.evalkit.framework.workflow.model.WorkflowNode;
import com.evalkit.framework.workflow.utils.WorkflowUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 评估器
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public abstract class Scorer extends WorkflowNode {
    protected ScorerConfig config;

    public Scorer() {
        this(ScorerConfig.builder().build());
    }

    public Scorer(ScorerConfig config) {
        super(WorkflowUtils.generateNodeId(NodeNamePrefix.SCORER));
        validConfig(config);
        this.config = config;
    }

    protected void validConfig(ScorerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config is null");
        }
        if (StringUtils.isEmpty(config.getMetricName())) {
            throw new IllegalArgumentException("metricName must not be empty");
        }
        if (config.getThreshold() < 0) {
            throw new IllegalArgumentException("limit must be more than or equals 0");
        }
        if (config.getThreadNum() < 1) {
            throw new IllegalArgumentException("threadNum must be more than or equals 1");
        }
        if (config.getTotalScore() < 0) {
            throw new IllegalArgumentException("totalScore must be more than or equals 0");
        }
    }

    /**
     * 评估前钩子
     */
    public void beforeEval(DataItem dataItem) {

    }

    /**
     * 评估
     */
    public abstract ScorerResult eval(DataItem dataItem) throws Exception;

    /**
     * 评估后钩子
     */
    public ScorerResult afterEval(DataItem dataItem, ScorerResult result) {
        return result;
    }

    /**
     * 错误处理钩子
     */
    public void orErrorEval(DataItem dataItem, Throwable ex) {
    }

    /**
     * 包含钩子的评估,单数据项评估失败不能影响整体运行
     */
    public ScorerResult evalWrapper(DataItem dataItem) {
        try {
            beforeEval(dataItem);
            return doEval(dataItem);
        } catch (Throwable e) {
            log.error("Scorer eval error, dataIndex={}", dataItem.getDataIndex(), e);
            orErrorEval(dataItem, e);
            return buildErrorResult(dataItem, e);
        } finally {
            // 钩子后置处理无论成功失败都要执行
            afterEval(dataItem, null /* 由子类决定是否复用结果 */);
        }
    }

    /**
     * 执行评估返回评估结果
     *
     * @param item 评测数据项
     * @return 评测结果
     * @throws Exception 评测异常
     */
    protected ScorerResult doEval(DataItem item) throws Exception {
        long start = System.currentTimeMillis();
        ScorerResult tmp = Objects.requireNonNull(eval(item), "eval() returned null");
        long end = System.currentTimeMillis();
        // 如果是动态总分数需要从评测结果中取,否则从配置中取
        double totalScore = config.isDynamicTotalScore() ? tmp.getTotalScore() : config.getTotalScore();
        double scoreRate = calcScoreRate(tmp.getScore(), totalScore);
        boolean pass = decidePass(tmp.getScore(), scoreRate);
        return ScorerResult.builder()
                .dataIndex(item.getDataIndex())
                .metric(tmp.getMetric())
                .score(tmp.getScore())
                .totalScore(totalScore)
                .reason(tmp.getReason())
                .extra(tmp.getExtra())
                .threshold(config.getThreshold())
                .star(config.isStar())
                .success(true)
                .statTime(start)
                .endTime(end)
                .timeCost(end - start)
                .scoreRate(scoreRate)
                .pass(pass)
                .build();
    }

    /**
     * 构建评测失败的结果
     *
     * @param item 当前数据项
     * @param ex   异常信息
     * @return 评测失败时失败结果
     */
    protected ScorerResult buildErrorResult(DataItem item, Throwable ex) {
        return ScorerResult.builder()
                .dataIndex(item.getDataIndex())
                .metric(config.getMetricName())
                .score(0)
                .totalScore(config.getTotalScore())
                .reason("Error: " + ex.getMessage())
                .success(false)
                .threshold(config.getThreshold())
                .star(config.isStar())
                .pass(false)
                .scoreRate(0)
                .build();
    }

    /**
     * 计算得分率
     *
     * @param score 当前分数
     * @param total 总分数
     * @return 得分率
     */
    protected static double calcScoreRate(double score, double total) {
        return total > 0 ? score / total : 0;
    }

    /**
     * 判断评估器是否通过
     *
     * @param scoreValue 分数值
     * @param scoreRate  得分率
     * @return 是否通过
     */
    protected boolean decidePass(double scoreValue, double scoreRate) {
        ScoreStrategy strategy = WorkflowContextOps.getScorerStrategy(getWorkflowContext());
        double threshold = config.getThreshold();
        if (strategy instanceof ScoreValueStrategy) {
            return scoreValue >= threshold;
        } else if (strategy instanceof ScoreRateStrategy) {
            return scoreRate >= threshold;
        } else {
            throw new IllegalArgumentException("Unsupported scorer strategy: " + strategy.getClass().getName());
        }
    }

    @Override
    public void doExecute() {
        long start = System.currentTimeMillis();
        WorkflowContext ctx = getWorkflowContext();
        List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
        ScoreStrategy scorerStrategy = WorkflowContextOps.getScorerStrategy(ctx);
        double threshold = WorkflowContextOps.getThreshold(ctx);
        if (CollectionUtils.isEmpty(dataItems)) {
            throw new EvalException("Data items is empty");
        }
        List<ScorerResult> scorerResults = BatchRunner.runBatch(dataItems, this::evalWrapper, PoolName.SCORER, config.getThreadNum(), size -> size * SINGLE_TASK_TIMEOUT);
        if (CollectionUtils.isEmpty(scorerResults)) {
            throw new EvalException("Scorer result is empty");
        }
        // 数据项添加评估结果,添加后会自动更新最终评测结果
        dataItems.forEach(dataItem -> scorerResults.stream()
                .filter(r -> Objects.equals(r.getDataIndex(), dataItem.getDataIndex()))
                .findFirst()
                .ifPresent(scorerResult -> dataItem.addScorerResult(scorerResult, scorerStrategy, threshold)));
        log.info("Scorer [{}] execute success, time cost: {}ms", config.getMetricName(), System.currentTimeMillis() - start);
    }
}
