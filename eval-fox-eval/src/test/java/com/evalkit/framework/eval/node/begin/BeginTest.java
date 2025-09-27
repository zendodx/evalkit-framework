package com.evalkit.framework.eval.node.begin;

import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.node.begin.config.BeginConfig;
import com.evalkit.framework.eval.node.scorer.strategy.SumScoreStrategy;
import com.evalkit.framework.workflow.WorkflowContextHolder;
import com.evalkit.framework.workflow.model.WorkflowContext;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BeginTest {
    @Test
    public void testDoExecute() {
        double threshold = 10.0;
        WorkflowContextHolder.set(new WorkflowContext());

        // 开始节点执行
        Begin begin = new Begin(BeginConfig.builder().threshold(10).build());
        begin.doExecute();

        // 验证开始节点本身的值
        assertTrue(StringUtils.isNotEmpty(begin.getId()));
        assertEquals(threshold, begin.config.getThreshold());
        assertInstanceOf(SumScoreStrategy.class, begin.config.getScoreStrategy());
        // 验证上下文的值
        WorkflowContext ctx = WorkflowContextHolder.get();
        assertEquals(threshold, WorkflowContextOps.getThreshold(ctx));
        assertInstanceOf(SumScoreStrategy.class, WorkflowContextOps.getScorerStrategy(ctx));
    }

}