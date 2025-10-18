package com.evalkit.framework.eval.context;

/**
 * 上下文的key
 */
public class WorkflowContextKey {
    private WorkflowContextKey() {
    }

    protected static final String TASK_NAME = "task_name";
    public static final String SCORE_STRATEGY = "score_strategy";
    public static final String THRESHOLD = "threshold";
    public static final String DATA_ITEM_LIST = "data_item_list";
    public static final String COUNT_RESULT_MAP = "count_result_map";
    public static final String EXTRA = "extra";
}
