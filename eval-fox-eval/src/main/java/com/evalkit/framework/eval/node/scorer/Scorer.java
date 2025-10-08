package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.common.thread.BatchRunner;
import com.evalkit.framework.common.thread.PoolName;
import com.evalkit.framework.eval.constants.NodeNamePrefix;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.scorer.config.ScorerConfig;
import com.evalkit.framework.workflow.model.WorkflowContext;
import com.evalkit.framework.workflow.model.WorkflowNode;
import com.evalkit.framework.workflow.utils.WorkflowUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

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
        this.config = config;
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
        ScorerResult result = new ScorerResult();
        result.setDataIndex(dataItem.getDataIndex());
        try {
            beforeEval(dataItem);
            long start = System.currentTimeMillis();
            ScorerResult resultTmp = eval(dataItem);
            long end = System.currentTimeMillis();
            if (resultTmp != null) {
                result.setMetric(resultTmp.getMetric());
                result.setScore(resultTmp.getScore());
                result.setReason(resultTmp.getReason());
                result.setExtra(resultTmp.getExtra());
                result.setStatTime(start);
                result.setEndTime(end);
                result.setTimeCost(end - start);
                result.setSuccess(true);
                result.setThreshold(config.getThreshold());
                result.setPass(resultTmp.getScore() >= config.getThreshold());
                result.setStar(config.isStar());
            }

        } catch (Throwable e) {
            log.error("Scorer eval error: ", e);
            orErrorEval(dataItem, e);
            // 评估出错返回错误的评估结果
            // 分数设置为-1标记评估出错,不参与后续指标计算
            result.setMetric(config.getMetricName());
            result.setScore(0);
            result.setReason("评测出错");
            result.setExtra(null);
            result.setSuccess(false);
            result.setThreshold(config.getThreshold());
            result.setPass(false);
            result.setStar(config.isStar());
        }
        return afterEval(dataItem, result);
    }


    @Override
    public void doExecute() {
        long start = System.currentTimeMillis();
        WorkflowContext ctx = getWorkflowContext();
        List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
        if (CollectionUtils.isEmpty(dataItems)) {
            throw new EvalException("Data items is empty");
        }
        List<ScorerResult> scorerResults = BatchRunner.runBatch(dataItems, this::evalWrapper, PoolName.SCORER, config.getThreadNum(), size -> size * SINGLE_TASK_TIMEOUT);
        if (CollectionUtils.isEmpty(scorerResults)) {
            throw new EvalException("Scorer result is empty");
        }
        // 数据项添加评估结果,添加后会自动更新最终评测结果
        dataItems.forEach(item -> scorerResults.stream().filter(r -> Objects.equals(r.getDataIndex(), item.getDataIndex()))
                .findFirst().ifPresent(item::addScorerResult));
        log.info("Scorer [{}] execute success, time cost: {}ms", config.getMetricName(), System.currentTimeMillis() - start);
    }
}
