package com.evalkit.framework.eval.facade.config;

import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.workflow.Workflow;

import java.util.Map;

/**
 * 全量式评测配置
 */
public class FullEvalConfig extends EvalConfig {
    /* 数据加载器 */
    protected DataLoader dataLoader;
    /* 评测工作流 */
    protected Workflow evalWorkflow;
    /* 结果上报工作流 */
    protected Workflow reportWorkflow;

    protected FullEvalConfig() {
    }

    protected FullEvalConfig(String taskName,
                             String filePath,
                             int offset,
                             int limit,
                             int threadNum,
                             double passScore,
                             Map<String, Object> extra,
                             DataLoader dataLoader,
                             Workflow evalWorkflow,
                             Workflow reportWorkflow) {
        super(taskName, filePath, offset, limit, threadNum, passScore, extra);
        this.dataLoader = dataLoader;
        this.evalWorkflow = evalWorkflow;
        this.reportWorkflow = reportWorkflow;
    }

    public static FullEvalConfigBuilder<?> builder() {
        return new FullEvalConfigBuilder<>();
    }


    public static class FullEvalConfigBuilder<B extends FullEvalConfigBuilder<B>> extends EvalConfigBuilder<B> {
        private DataLoader dataLoader;
        private Workflow evalWorkflow;
        private Workflow reportWorkflow;

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

        @Override
        public FullEvalConfig build() {
            FullEvalConfig fullEvalConfig = new FullEvalConfig(taskName, filePath, offset, limit, threadNum, passScore, extra,
                    dataLoader, evalWorkflow, reportWorkflow);
            fullEvalConfig.updateConfigFromEnv();
            fullEvalConfig.checkParams();
            return fullEvalConfig;
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
