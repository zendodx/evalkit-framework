package com.evalkit.framework.eval.facade.config;

import com.evalkit.framework.common.utils.runtime.RuntimeEnvUtils;
import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.workflow.Workflow;

import java.util.Map;

/**
 * 增量评测配置
 */
public class DeltaEvalConfig extends FullEvalConfig {
    /* 批处理数量,默认10 */
    protected int batchSize;
    /* 结果上报间隔,默认30秒 */
    protected int reportInterval;
    /* MQ消息接收超时时间,默认10000毫秒 */
    protected int mqReceiveTimeout;
    /* 是否开启断点续评, 默认true */
    protected boolean enableResume;
    /* 单消息处理最大时间,60秒 */
    protected long messageProcessMaxTime;


    protected DeltaEvalConfig() {
    }

    protected DeltaEvalConfig(String taskName,
                              String filePath,
                              int offset,
                              int limit,
                              int threadNum,
                              double passScore,
                              Map<String, Object> extra,
                              boolean openInjectData,
                              boolean injectDataIndex,
                              boolean injectInputData,
                              boolean injectApiCompletionResult,
                              boolean injectEvalResult,
                              boolean injectExtra,
                              DataLoader dataLoader,
                              Workflow evalWorkflow,
                              Workflow reportWorkflow,
                              int batchSize,
                              int reportInterval,
                              int mqReceiveTimeout,
                              boolean enableResume,
                              long messageProcessMaxTime) {
        super(taskName, filePath, offset, limit, threadNum, passScore, extra,
                openInjectData, injectDataIndex, injectInputData, injectApiCompletionResult, injectEvalResult, injectExtra,
                dataLoader, evalWorkflow, reportWorkflow);
        this.dataLoader = dataLoader;
        this.evalWorkflow = evalWorkflow;
        this.reportWorkflow = reportWorkflow;
        this.batchSize = batchSize;
        this.reportInterval = reportInterval;
        this.mqReceiveTimeout = mqReceiveTimeout;
        this.enableResume = enableResume;
        this.messageProcessMaxTime = messageProcessMaxTime;
    }

    public static DeltaEvalConfigBuilder<?> builder() {
        return new DeltaEvalConfigBuilder<>();
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
        Long messageProcessMaxTime = RuntimeEnvUtils.getJVMPropertyLong("messageProcessMaxTime", null);
        if (messageProcessMaxTime != null && messageProcessMaxTime > 0) {
            this.messageProcessMaxTime = messageProcessMaxTime;
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
        if (messageProcessMaxTime <= 0) {
            throw new IllegalArgumentException("messageProcessMaxTime must be greater than 0");
        }
    }

    public static class DeltaEvalConfigBuilder<B extends DeltaEvalConfigBuilder<B>> extends FullEvalConfigBuilder<B> {
        /* 子类特有字段 */
        protected int batchSize = 10;
        protected int reportInterval = 30;
        protected int mqReceiveTimeout = 10000;
        protected boolean enableResume = true;
        protected long messageProcessMaxTime = 60;

        public B batchSize(int batchSize) {
            this.batchSize = batchSize;
            return (B) this;
        }

        public B reportInterval(int reportInterval) {
            this.reportInterval = reportInterval;
            return (B) this;
        }

        public B mqReceiveTimeout(int mqReceiveTimeout) {
            this.mqReceiveTimeout = mqReceiveTimeout;
            return (B) this;
        }

        public B enableResume(boolean enableResume) {
            this.enableResume = enableResume;
            return (B) this;
        }

        public B messageProcessMaxTime(long messageProcessMaxTime) {
            this.messageProcessMaxTime = messageProcessMaxTime;
            return (B) this;
        }

        @Override
        public DeltaEvalConfig build() {
            DeltaEvalConfig deltaEvalConfig = new DeltaEvalConfig(
                    taskName, filePath, offset, limit, threadNum, passScore, extra,
                    openInjectData, injectDataIndex, injectInputData, injectApiCompletionResult, injectEvalResult, injectExtra,
                    dataLoader, evalWorkflow, reportWorkflow, batchSize, reportInterval,
                    mqReceiveTimeout, enableResume, messageProcessMaxTime);
            deltaEvalConfig.updateConfigFromEnv();
            deltaEvalConfig.checkParams();
            return deltaEvalConfig;
        }
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

    public long getMessageProcessMaxTime() {
        return messageProcessMaxTime;
    }

    public void setMessageProcessMaxTime(long messageProcessMaxTime) {
        this.messageProcessMaxTime = messageProcessMaxTime;
    }
}
