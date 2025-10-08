package com.evalkit.framework.eval.facade;

/**
 * 评测门面
 */
public abstract class EvalFacade {

    /**
     * 加载数据前钩子
     */
    protected void beforeLoadData() {
    }

    /**
     * 加载数据后钩子
     */
    protected void afterLoadData() {
    }

    /**
     * 加载数据
     */
    protected abstract void loadData();

    /**
     * 加载数据生命周期
     */
    protected void loadDataWrapper() {
        beforeLoadData();
        loadData();
        afterLoadData();
    }

    /**
     * 评测前钩子
     */
    protected void beforeEval() {
    }

    /**
     * 评测后钩子
     */
    protected void afterEval() {
    }

    /**
     * 评测
     */
    protected abstract Object eval();

    /**
     * 评测生命周期
     */
    protected void evalWrapper() {
        beforeEval();
        eval();
        afterEval();
    }

    /**
     * 结果上报前钩子
     */
    protected void beforeReport() {

    }

    /**
     * 结果上报后钩子
     */
    protected void afterReport() {

    }

    /**
     * 结果上报
     */
    protected abstract void report();

    /**
     * 结果上报生命周期
     */
    protected void reportWrapper() {
        beforeReport();
        report();
        afterReport();
    }

    /**
     * 评测执行周期
     */
    public void execute() throws Exception {
        beforeExecute();
        doExecute();
        afterExecute();
    }

    /**
     * 实际操作
     */
    protected void doExecute() {
        loadDataWrapper();
        evalWrapper();
        reportWrapper();
    }

    /**
     * 执行前钩子
     */
    protected void beforeExecute() {
    }

    /**
     * 执行后钩子
     */
    protected void afterExecute() {
    }

    /**
     * 获取待处理数据量
     */
    public abstract long getRemainDataCount();

    /**
     * 获取已处理数据量
     */
    public abstract long getProcessedDataCount();
}
