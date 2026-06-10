package com.evalkit.framework.eval.facade.config;

import com.evalkit.framework.common.utils.runtime.RuntimeEnvUtils;

/**
 * 增量评测配置
 */
public class DeltaEvalConfig extends FullEvalConfig {
    protected int batchSize;
    protected int reportInterval;
    protected int mqReceiveTimeout;
    protected boolean enableResume;
    protected long messageProcessMaxTime;
    protected boolean enablePeriodicReport;

    protected DeltaEvalConfig() {
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
        Boolean enablePeriodicReport = RuntimeEnvUtils.getJVMPropertyBoolean("enablePeriodicReport", null);
        if (enablePeriodicReport != null) {
            this.enablePeriodicReport = enablePeriodicReport;
        }
    }

    @Override
    protected void checkParams() {
        super.checkParams();
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than 0");
        }
        if (enablePeriodicReport && reportInterval <= 0) {
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
        /* 批处理大小 */
        protected int batchSize = 10;
        /* 报告间隔, 10分钟 */
        protected int reportInterval = 600;
        /* MQ接收超时时间, 10秒 */
        protected int mqReceiveTimeout = 10000;
        /* 是否开启中断续评模式 */
        protected boolean enableResume = true;
        /* 消息处理最大时间, 60秒 */
        protected long messageProcessMaxTime = 60;
        /* 是否开启周期性上报，默认开启 */
        protected boolean enablePeriodicReport = true;

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

        public B enablePeriodicReport(boolean enablePeriodicReport) {
            this.enablePeriodicReport = enablePeriodicReport;
            return (B) this;
        }

        @Override
        protected void applyTo(EvalConfig base) {
            super.applyTo(base);
            DeltaEvalConfig config = (DeltaEvalConfig) base;
            config.batchSize = batchSize;
            config.reportInterval = reportInterval;
            config.mqReceiveTimeout = mqReceiveTimeout;
            config.enableResume = enableResume;
            config.messageProcessMaxTime = messageProcessMaxTime;
            config.enablePeriodicReport = enablePeriodicReport;
        }

        @Override
        public DeltaEvalConfig build() {
            DeltaEvalConfig config = new DeltaEvalConfig();
            applyTo(config);
            config.updateConfigFromEnv();
            config.checkParams();
            return config;
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

    public boolean isEnablePeriodicReport() {
        return enablePeriodicReport;
    }

    public void setEnablePeriodicReport(boolean enablePeriodicReport) {
        this.enablePeriodicReport = enablePeriodicReport;
    }
}
