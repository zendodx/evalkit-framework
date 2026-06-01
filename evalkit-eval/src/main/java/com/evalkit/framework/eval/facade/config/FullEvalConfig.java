package com.evalkit.framework.eval.facade.config;

import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.workflow.Workflow;

/**
 * 全量式评测配置
 */
public class FullEvalConfig extends EvalConfig {
    protected DataLoader dataLoader;
    protected Workflow evalWorkflow;
    protected Workflow reportWorkflow;

    protected FullEvalConfig() {
    }

    public static FullEvalConfigBuilder<?> builder() {
        return new FullEvalConfigBuilder<>();
    }

    public static class FullEvalConfigBuilder<B extends FullEvalConfigBuilder<B>> extends EvalConfigBuilder<B> {
        /* 数据加载器 */
        protected DataLoader dataLoader;
        /* 评测工作流 */
        protected Workflow evalWorkflow;
        /* 结果上报工作流 */
        protected Workflow reportWorkflow;

        public B dataLoader(DataLoader dataLoader) {
            this.dataLoader = dataLoader;
            return (B) this;
        }

        public B evalWorkflow(Workflow evalWorkflow) {
            this.evalWorkflow = evalWorkflow;
            return (B) this;
        }

        public B reportWorkflow(Workflow reportWorkflow) {
            this.reportWorkflow = reportWorkflow;
            return (B) this;
        }

        /**
         * 将 Builder 字段填入 config 对象（供子类 build() 复用）
         */
        @Override
        protected void applyTo(EvalConfig base) {
            super.applyTo(base);
            FullEvalConfig config = (FullEvalConfig) base;
            config.dataLoader = dataLoader;
            config.evalWorkflow = evalWorkflow;
            config.reportWorkflow = reportWorkflow;
        }

        @Override
        public FullEvalConfig build() {
            FullEvalConfig config = new FullEvalConfig();
            applyTo(config);
            config.updateConfigFromEnv();
            config.checkParams();
            return config;
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
}
