package com.evalkit.framework.eval.constants;

/**
 * 环境变量参数key,可通过mvn参数动态更新运行时的值,环境变量的优先级最高,在执行前设置
 */
public class EnvParams {
    /* ---- begin ---- */
    /* 评测分数通过阈值 */
    public static final String PASS_THRESHOLD = "passThreshold";
    /* 评估器分数整合策略 */
    public static final String SCORE_STRATEGY = "scoreStrategy";

    /* ---- dataLoader ---- */
    /* 页码 */
    public static final String OFFSET = "offset";
    /* 页大小 */
    public static final String LIMIT = "limit";
    /* 评测文件路径 */
    public static final String FILE_PATH = "filePath";

    /* ---- apiCompletion ---- */
    /* 接口调用线程数 */
    public static final String API_THREAD_NUM = "apiThreadNum";

    /* ---- scorer ---- */
    /* 评测线程数 */
    public static final String EVAL_THREAD_NUM = "evalThreadNum";

    /* ---- evalAdminReporter ---- */
    /* 用户名 */
    public static final String USERNAME = "username";
    /* 密码 */
    public static final String PASSWORD = "password";
    /* 任务id */
    public static final String TASK_ID = "taskId";
    /* 工作空间id */
    public static final String WORKSPACE_ID = "workspaceId";
    /* 后台地址 */
    public static final String BASE_URL = "baseUrl";
}