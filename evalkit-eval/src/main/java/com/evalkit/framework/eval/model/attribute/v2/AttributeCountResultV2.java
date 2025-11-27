package com.evalkit.framework.eval.model.attribute.v2;

import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.model.CountResult;
import com.evalkit.framework.workflow.model.WorkflowContext;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AttributeCountResultV2 implements CountResult {
    /* 统计结果名称 */
    private final String counterName = "attributeCountResultV2";
    /* 整体评测结果归因 */
    private List<Category> categories = new ArrayList<>();

    @Override
    public void writeToCtx(WorkflowContext ctx) {
        WorkflowContextOps.setCountResult(ctx, this);
    }

    @Override
    public String counterName() {
        return counterName;
    }
}
