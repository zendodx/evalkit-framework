package com.evalkit.framework.eval.node.debuger;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.node.scorer.strategy.ScoreStrategy;
import com.evalkit.framework.workflow.model.WorkflowContext;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.File;
import java.util.Map;

/**
 * json文件调试器
 */
public class JsonFileDebugger extends Debugger {
    protected File file;

    public JsonFileDebugger(File file) {
        this(0, -1, true, file);
    }

    public JsonFileDebugger(int offset, int limit, File file) {
        this(offset, limit, true, file);
    }

    public JsonFileDebugger(boolean containsEvalResult, File file) {
        this(0, -1, containsEvalResult, file);
    }

    public JsonFileDebugger(int offset, int limit, boolean containsEvalResult, File file) {
        super(offset, limit, containsEvalResult);
        this.file = file;
    }

    @Override
    protected void inject() {
        WorkflowContext ctx = getWorkflowContext();
        Map<String, Object> map = JsonUtils.readJsonFile(file, new TypeReference<Map<String, Object>>() {
        });
        ScoreStrategy strategy = WorkflowContextOps.getScorerStrategy(ctx);
        double threshold = WorkflowContextOps.getThreshold(ctx);
        WorkflowContextOps.setDataItems(ctx, buildDataItems(map, 0L, strategy, threshold));
        buildCountResultMap(map);
        WorkflowContextOps.setCountResults(ctx, buildCountResultMap(map));
    }
}
