package com.evalkit.framework.eval.model;

import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.workflow.model.WorkflowContext;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 问题归因结果
 */
@Data
public class AttributeCountResult implements CountResult {
    /* 统计结果名称 */
    private final String counterName = "attributeCountResult";
    /* 整体评测结果归因,按照问题类型聚合,key是问题类型,value是Case列表 */
    private List<Attribute> overallAttribution;

    /**
     * 添加归因口径
     */
    public void addAttribute(Attribute attrib) {
        if (overallAttribution == null) {
            overallAttribution = new ArrayList<>();
        }
        overallAttribution.add(attrib);
    }

    @Override
    public void writeToCtx(WorkflowContext ctx) {
        WorkflowContextOps.setCountResult(ctx, this);
    }

    @Override
    public String counterName() {
        return counterName;
    }
}
