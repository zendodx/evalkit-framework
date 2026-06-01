package com.evalkit.framework.eval.facade.config;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.common.utils.random.UuidUtils;
import com.evalkit.framework.common.utils.runtime.RuntimeEnvUtils;
import com.evalkit.framework.common.utils.time.DateUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 评测运行基础配置
 */
public class EvalConfig {
    protected String taskName;
    protected String taskNameUuid;
    protected String attachDir;
    protected String filePath;
    protected int offset;
    protected int limit;
    protected int threadNum;
    protected double passScore;
    protected Map<String, Object> extra;
    protected boolean enableInjectData;
    protected boolean enableInjectDataIndex;
    protected boolean enableInjectInputData;
    protected boolean enableInjectApiCompletionResult;
    protected boolean enableInjectEvalResult;
    protected boolean enableInjectExtra;

    protected EvalConfig() {
    }

    /**
     * 获取额外配置
     */
    public Object getExtraConfig(String key) {
        if (extra != null) {
            return extra.getOrDefault(key, null);
        }
        return null;
    }

    /**
     * 设置额外配置
     */
    public void setExtraConfig(String key, Object value) {
        if (extra == null) {
            extra = new HashMap<>();
        }
        extra.put(key, value);
    }

    /**
     * 读取环境变量更新配置参数
     */
    protected void updateConfigFromEnv() {
        String taskName = RuntimeEnvUtils.getJVMPropertyString("taskName", null);
        if (StringUtils.isNotEmpty(taskName)) {
            this.taskName = taskName;
            // taskName 变更时同步更新依赖它的派生字段
            this.taskNameUuid = UuidUtils.generateUuidByKey(this.taskName);
            if (this.attachDir == null || this.attachDir.isEmpty()) {
                this.attachDir = "attachments/" + this.taskName;
            }
        }
        String attachDir = RuntimeEnvUtils.getJVMPropertyString("attachDir", null);
        if (StringUtils.isNotEmpty(attachDir)) {
            this.attachDir = attachDir;
        }
        String filePath = RuntimeEnvUtils.getJVMPropertyString("filePath", null);
        if (StringUtils.isNotEmpty(filePath)) {
            this.filePath = filePath;
        }
        Integer offset = RuntimeEnvUtils.getJVMPropertyInt("offset", null);
        if (offset != null && offset > 0) {
            this.offset = offset;
        }
        Integer limit = RuntimeEnvUtils.getJVMPropertyInt("limit", null);
        if (limit != null && limit >= 0) {
            this.limit = limit;
        }
        Integer threadNum = RuntimeEnvUtils.getJVMPropertyInt("threadNum", null);
        if (threadNum != null && threadNum > 0) {
            this.threadNum = threadNum;
        }
        Double passScore = RuntimeEnvUtils.getJVMPropertyDouble("passScore", null);
        if (passScore != null && passScore > 0.0) {
            this.passScore = passScore;
        }
        // extra是json格式
        String extra = RuntimeEnvUtils.getJVMPropertyString("extra", null);
        if (StringUtils.isNotEmpty(extra)) {
            this.extra = JsonUtils.fromJson(extra, new TypeReference<Map<String, Object>>() {
            });
        }
        Boolean enableInjectData = RuntimeEnvUtils.getJVMPropertyBoolean("enableInjectData", null);
        if (enableInjectData != null) {
            this.enableInjectData = enableInjectData;
        }
        Boolean enableInjectDataIndex = RuntimeEnvUtils.getJVMPropertyBoolean("enableInjectDataIndex", null);
        if (enableInjectDataIndex != null) {
            this.enableInjectDataIndex = enableInjectDataIndex;
        }
        Boolean enableInjectInputData = RuntimeEnvUtils.getJVMPropertyBoolean("enableInjectInputData", null);
        if (enableInjectInputData != null) {
            this.enableInjectInputData = enableInjectInputData;
        }
        Boolean enableInjectApiCompletionResult = RuntimeEnvUtils.getJVMPropertyBoolean("enableInjectApiCompletionResult", null);
        if (enableInjectApiCompletionResult != null) {
            this.enableInjectApiCompletionResult = enableInjectApiCompletionResult;
        }
        Boolean enableInjectEvalResult = RuntimeEnvUtils.getJVMPropertyBoolean("enableInjectEvalResult", null);
        if (enableInjectEvalResult != null) {
            this.enableInjectEvalResult = enableInjectEvalResult;
        }
        Boolean enableInjectExtra = RuntimeEnvUtils.getJVMPropertyBoolean("enableInjectExtra", null);
        if (enableInjectExtra != null) {
            this.enableInjectExtra = enableInjectExtra;
        }
    }

    /**
     * 参数校验
     */
    protected void checkParams() {
        if (StringUtils.isEmpty(taskName)) {
            throw new IllegalArgumentException("taskName is empty");
        }
        if (threadNum <= 0) {
            throw new IllegalArgumentException("threadNum must be greater than 0");
        }
        if (passScore < 0.0) {
            throw new IllegalArgumentException("passScore must be greater than or equal to 0");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be greater than or equal to 0");
        }
        if (limit != -1 && limit < 0) {
            throw new IllegalArgumentException("limit must be greater than 0 or equal to -1");
        }
    }

    public static EvalConfigBuilder<?> builder() {
        return new EvalConfigBuilder<>();
    }

    public static class EvalConfigBuilder<B extends EvalConfigBuilder<B>> {
        /* 任务名称,增量评测统一任务需保持任务名称一致, 默认 EvalTest_运行时间 */
        protected String taskName = "EvalTest_" + DateUtils.nowToString("yyyyMMddHHmmss");
        /* taskName对应的uuid,用于ActiveMQ和SQLite的文件名称 */
        protected String taskNameUuid = UuidUtils.generateUuidByKey(taskName);
        /* 附件输出目录，默认为 attachments/{taskName}，所有 FileReporter 输出文件统一写入此目录 */
        protected String attachDir;
        /* 评测数据集文件路径,默认空*/
        protected String filePath;
        /* 分页偏移量, 默认0 */
        protected int offset = 0;
        /* 分页页大小, 默认-1,加载所有 */
        protected int limit = -1;
        /* 并发数,默认1 */
        protected int threadNum = 1;
        /* 及格分数,默认0 */
        protected double passScore = 0.0;
        /* 额外配置 */
        protected Map<String, Object> extra;
        /* 开启输入注入, 默认不开启 */
        protected boolean enableInjectData = false;
        /* 按需注入数据索引 */
        protected boolean enableInjectDataIndex = true;
        /* 按需注入输入数据 */
        protected boolean enableInjectInputData = true;
        /* 按需注入接口完成结果 */
        protected boolean enableInjectApiCompletionResult = true;
        /* 按需注入评测结果 */
        protected boolean enableInjectEvalResult = true;
        /* 按需注入额外配置 */
        protected boolean enableInjectExtra = true;

        protected EvalConfigBuilder() {
        }

        public B taskName(String taskName) {
            this.taskName = taskName;
            return (B) this;
        }

        public B attachDir(String attachDir) {
            this.attachDir = attachDir;
            return (B) this;
        }

        public B filePath(String filePath) {
            this.filePath = filePath;
            return (B) this;
        }

        public B offset(int offset) {
            this.offset = offset;
            return (B) this;
        }

        public B limit(int limit) {
            this.limit = limit;
            return (B) this;
        }

        public B threadNum(int threadNum) {
            this.threadNum = threadNum;
            return (B) this;
        }

        public B passScore(double passScore) {
            this.passScore = passScore;
            return (B) this;
        }

        public B extra(Map<String, Object> extra) {
            this.extra = extra;
            return (B) this;
        }

        public B openInjectData(boolean enableInjectData) {
            this.enableInjectData = enableInjectData;
            return (B) this;
        }

        public B injectDataIndex(boolean enableInjectDataIndex) {
            this.enableInjectDataIndex = enableInjectDataIndex;
            return (B) this;
        }

        public B injectInputData(boolean enableInjectInputData) {
            this.enableInjectInputData = enableInjectInputData;
            return (B) this;
        }

        public B injectApiCompletionResult(boolean enableInjectApiCompletionResult) {
            this.enableInjectApiCompletionResult = enableInjectApiCompletionResult;
            return (B) this;
        }

        public B injectEvalResult(boolean enableInjectEvalResult) {
            this.enableInjectEvalResult = enableInjectEvalResult;
            return (B) this;
        }

        public B injectExtra(boolean enableInjectExtra) {
            this.enableInjectExtra = enableInjectExtra;
            return (B) this;
        }

        /**
         * 将 Builder 字段填入 config 对象（供子类 build() 复用）
         */
        protected void applyTo(EvalConfig config) {
            config.taskName = taskName;
            config.taskNameUuid = UuidUtils.generateUuidByKey(taskName);
            config.attachDir = (attachDir != null && !attachDir.isEmpty()) ? attachDir : "attachments/" + taskName;
            config.filePath = filePath;
            config.offset = offset;
            config.limit = limit;
            config.threadNum = threadNum;
            config.passScore = passScore;
            config.extra = extra;
            config.enableInjectData = enableInjectData;
            config.enableInjectDataIndex = enableInjectDataIndex;
            config.enableInjectInputData = enableInjectInputData;
            config.enableInjectApiCompletionResult = enableInjectApiCompletionResult;
            config.enableInjectEvalResult = enableInjectEvalResult;
            config.enableInjectExtra = enableInjectExtra;
        }

        /**
         * 最终 build：一定会触发环境变量覆盖 + 校验
         */
        public EvalConfig build() {
            EvalConfig config = new EvalConfig();
            applyTo(config);
            config.updateConfigFromEnv();
            config.checkParams();
            return config;
        }
    }

    public String getAttachDir() {
        return attachDir;
    }

    public void setAttachDir(String attachDir) {
        this.attachDir = attachDir;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getThreadNum() {
        return threadNum;
    }

    public void setThreadNum(int threadNum) {
        this.threadNum = threadNum;
    }

    public double getPassScore() {
        return passScore;
    }

    public void setPassScore(double passScore) {
        this.passScore = passScore;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }

    public boolean isEnableInjectData() {
        return enableInjectData;
    }

    public void setEnableInjectData(boolean enableInjectData) {
        this.enableInjectData = enableInjectData;
    }

    public boolean isEnableInjectDataIndex() {
        return enableInjectDataIndex;
    }

    public void setEnableInjectDataIndex(boolean enableInjectDataIndex) {
        this.enableInjectDataIndex = enableInjectDataIndex;
    }

    public boolean isEnableInjectInputData() {
        return enableInjectInputData;
    }

    public void setEnableInjectInputData(boolean enableInjectInputData) {
        this.enableInjectInputData = enableInjectInputData;
    }

    public boolean isEnableInjectApiCompletionResult() {
        return enableInjectApiCompletionResult;
    }

    public void setEnableInjectApiCompletionResult(boolean enableInjectApiCompletionResult) {
        this.enableInjectApiCompletionResult = enableInjectApiCompletionResult;
    }

    public boolean isEnableInjectEvalResult() {
        return enableInjectEvalResult;
    }

    public void setEnableInjectEvalResult(boolean enableInjectEvalResult) {
        this.enableInjectEvalResult = enableInjectEvalResult;
    }

    public boolean isEnableInjectExtra() {
        return enableInjectExtra;
    }

    public void setEnableInjectExtra(boolean enableInjectExtra) {
        this.enableInjectExtra = enableInjectExtra;
    }

    public String getTaskNameUuid() {
        return taskNameUuid;
    }

    public void setTaskNameUuid(String taskNameUuid) {
        this.taskNameUuid = taskNameUuid;
    }
}
