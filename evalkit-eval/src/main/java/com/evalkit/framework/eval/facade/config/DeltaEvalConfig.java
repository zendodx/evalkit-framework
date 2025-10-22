package com.evalkit.framework.eval.facade.config;

import com.evalkit.framework.common.utils.runtime.RuntimeEnvUtils;
import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.workflow.Workflow;

import java.util.Map;

/**
 * 增量评测配置
 */
public class DeltaEvalConfig extends EvalConfig {
    /* 数据加载器 */
    private DataLoader dataLoader;
    /* 评测工作流,必填 */
    private Workflow evalWorkflow;
    /* 评测结果上报工作流,必填 */
    private Workflow reportWorkflow;
    /* 批处理数量,默认1 */
    int batchSize;
    /* 结果上报间隔,默认30秒 */
    int reportInterval;
    /* MQ消息接收超时时间,默认10000毫秒 */
    int mqReceiveTimeout;
    /* 是否开启断点续评, 默认true */
    boolean enableResume;

    protected DeltaEvalConfig(String taskName,
                              String filePath,
                              int offset,
                              int limit,
                              int threadNum,
                              double passScore,
                              Map<String, Object> extra,
                              DataLoader dataLoader,
                              Workflow evalWorkflow,
                              Workflow reportWorkflow,
                              int batchSize,
                              int reportInterval,
                              int mqReceiveTimeout,
                              boolean enableResume) {
        super(taskName, filePath, offset, limit, threadNum, passScore, extra);
        this.dataLoader = dataLoader;
        this.evalWorkflow = evalWorkflow;
        this.reportWorkflow = reportWorkflow;
        this.batchSize = batchSize;
        this.reportInterval = reportInterval;
        this.mqReceiveTimeout = mqReceiveTimeout;
        this.enableResume = enableResume;
    }

    public static DeltaEvalConfigBuilder builder() {
        return new DeltaEvalConfigBuilder();
    }

    @Override
    public void updateConfigFromEnv() {
        super.updateConfigFromEnv();
        Integer batchSize = RuntimeEnvUtils.getJVMPropertyInt("batchSize", null);
        if (batchSize != null && batchSize > 1) {
            this.batchSize = batchSize;
        }
        Integer reportInterval = RuntimeEnvUtils.getJVMPropertyInt("reportInterval", null);
        if (reportInterval != null && reportInterval > 0) {
            this.reportInterval = reportInterval;
        }
        Integer mqReceiveTimeout = RuntimeEnvUtils.getJVMPropertyInt("mqReceiveTimeout", null);
        if (mqReceiveTimeout != null && mqReceiveTimeout > 0) {
            this.mqReceiveTimeout = mqReceiveTimeout;
        }
        Boolean enableResume = RuntimeEnvUtils.getJVMPropertyBoolean("enableResume", null);
        if (enableResume != null) {
            this.enableResume = enableResume;
        }
    }

    @Override
    protected void checkParams() {
        super.checkParams();
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than 0");
        }
        if (reportInterval <= 0) {
            throw new IllegalArgumentException("reportInterval must be greater than 0");
        }
        if (mqReceiveTimeout <= 0) {
            throw new IllegalArgumentException("mqReceiveTimeout must be greater than 0");
        }
    }

    public static class DeltaEvalConfigBuilder extends EvalConfigBuilder<DeltaEvalConfigBuilder> {
        /* 子类特有字段 */
        protected DataLoader dataLoader;
        protected Workflow evalWorkflow;
        protected Workflow reportWorkflow;
        protected int batchSize = 1;
        protected int reportInterval = 30;
        protected int mqReceiveTimeout = 10000;
        protected boolean enableResume = true;

        public DeltaEvalConfigBuilder dataLoader(DataLoader dataLoader) {
            this.dataLoader = dataLoader;
            return this;
        }

        public DeltaEvalConfigBuilder evalWorkflow(Workflow evalWorkflow) {
            this.evalWorkflow = evalWorkflow;
            return this;
        }

        public DeltaEvalConfigBuilder reportWorkflow(Workflow reportWorkflow) {
            this.reportWorkflow = reportWorkflow;
            return this;
        }

        public DeltaEvalConfigBuilder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public DeltaEvalConfigBuilder reportInterval(int reportInterval) {
            this.reportInterval = reportInterval;
            return this;
        }

        public DeltaEvalConfigBuilder mqReceiveTimeout(int mqReceiveTimeout) {
            this.mqReceiveTimeout = mqReceiveTimeout;
            return this;
        }

        public DeltaEvalConfigBuilder enableResume(boolean enableResume) {
            this.enableResume = enableResume;
            return this;
        }

        @Override
        public DeltaEvalConfig build() {
            DeltaEvalConfig deltaEvalConfig = new DeltaEvalConfig(
                    taskName, filePath, offset, limit, threadNum, passScore, extra,
                    dataLoader, evalWorkflow, reportWorkflow, batchSize, reportInterval, mqReceiveTimeout, enableResume);
            deltaEvalConfig.updateConfigFromEnv();
            deltaEvalConfig.checkParams();
            return deltaEvalConfig;
        }
    }

    public DataLoader getDataLoader() {
        return dataLoader;
    }

    public void setDataLoader(DataLoader dataLoader) {
        this.dataLoader = dataLoader;
    }

    public Workflow getEvalWorkflow() {
        return evalWorkflow;
    }

    public void setEvalWorkflow(Workflow evalWorkflow) {
        this.evalWorkflow = evalWorkflow;
    }

    public Workflow getReportWorkflow() {
        return reportWorkflow;
    }

    public void setReportWorkflow(Workflow reportWorkflow) {
        this.reportWorkflow = reportWorkflow;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getReportInterval() {
        return reportInterval;
    }

    public void setReportInterval(int reportInterval) {
        this.reportInterval = reportInterval;
    }

    public int getMqReceiveTimeout() {
        return mqReceiveTimeout;
    }

    public void setMqReceiveTimeout(int mqReceiveTimeout) {
        this.mqReceiveTimeout = mqReceiveTimeout;
    }

    public boolean isEnableResume() {
        return enableResume;
    }

    public void setEnableResume(boolean enableResume) {
        this.enableResume = enableResume;
    }
}
