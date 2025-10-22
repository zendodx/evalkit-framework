package com.evalkit.framework.eval.facade.config;

import com.evalkit.framework.common.utils.json.JsonUtils;
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
    /* 任务名称,增量评测统一任务需保持任务名称一致, 默认 EvalTest_运行时间 */
    protected String taskName;
    /* 评测数据集文件路径,默认空*/
    protected String filePath;
    /* 分页偏移量, 默认0 */
    protected int offset;
    /* 分页页大小, 默认-1,加载所有 */
    protected int limit;
    /* 并发数,默认1 */
    protected int threadNum;
    /* 评测通过分数 */
    protected double passScore;
    /* 额外配置 */
    protected Map<String, Object> extra;

    protected EvalConfig() {

    }

    protected EvalConfig(String taskName,
                         String filePath,
                         int offset,
                         int limit,
                         int threadNum,
                         double passScore,
                         Map<String, Object> extra) {
        this.taskName = taskName;
        this.filePath = filePath;
        this.offset = offset;
        this.limit = limit;
        this.threadNum = threadNum;
        this.passScore = passScore;
        this.extra = extra;
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
        }
        String filePath = RuntimeEnvUtils.getJVMPropertyString("filePath", null);
        if (StringUtils.isNotEmpty(filePath)) {
            this.filePath = filePath;
        }
        Integer offset = RuntimeEnvUtils.getJVMPropertyInt("offset", null);
        if (offset != null && offset > 0) {
            this.offset = offset;
        }
        Integer limit = RuntimeEnvUtils.getJVMPropertyInt("limit", -1);
        if (limit != null && limit >= 0) {
            this.limit = limit;
        }
        Integer threadNum = RuntimeEnvUtils.getJVMPropertyInt("threadNum", 1);
        if (threadNum != null && threadNum > 0) {
            this.threadNum = threadNum;
        }
        Double passScore = RuntimeEnvUtils.getJVMPropertyDouble("passScore", 0.0);
        if (passScore != null && passScore > 0.0) {
            this.passScore = passScore;
        }
        // extra是json格式
        String extra = RuntimeEnvUtils.getJVMPropertyString("extra", "");
        if (StringUtils.isNotEmpty(extra)) {
            this.extra = JsonUtils.fromJson(extra, new TypeReference<Map<String, Object>>() {
            });
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

    public static EvalConfigBuilder builder() {
        return new EvalConfigBuilder();
    }

    public static class EvalConfigBuilder<B extends EvalConfigBuilder<B>> {
        protected String taskName = "EvalTest_" + DateUtils.nowToString("yyyyMMddHHmmss");
        protected String filePath;
        protected int offset = 0;
        protected int limit = -1;
        protected int threadNum = 1;
        protected double passScore = 0.0;
        protected Map<String, Object> extra;

        protected EvalConfigBuilder() {
        }

        public B taskName(String taskName) {
            this.taskName = taskName;
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

        /**
         * 最终 build：一定会触发环境变量覆盖 + 校验
         */
        public EvalConfig build() {
            EvalConfig evalConfig = new EvalConfig(taskName, filePath, offset, limit, threadNum, passScore, extra);
            evalConfig.updateConfigFromEnv();
            evalConfig.checkParams();
            return evalConfig;
        }
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
}
