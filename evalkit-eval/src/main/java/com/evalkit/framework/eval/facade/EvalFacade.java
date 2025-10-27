package com.evalkit.framework.eval.facade;

/**
 * 抽象评测门面
 * 评测生命周期: 初始化,执行前,执行(加载数据前,加载数据,加载数据后,评测前,评测,评测后,结果上报前,结果上报,结果上报后),执行后
 */
public abstract class EvalFacade {

    /**
     * 评测入口
     */
    public void run() {
        init();
        executeWrapper();
    }

    /**
     * 初始化
     */
    protected abstract void init();

    /**
     * 评测执行生命周期
     */
    public void executeWrapper() {
        beforeExecute();
        execute();
        afterExecute();
    }

    /**
     * 执行前钩子
     */
    protected void beforeExecute() {
    }

    /**
     * 评测执行周期
     */
    protected void execute() {
        loadDataWrapper();
        evalWrapper();
        reportWrapper();
    }

    /**
     * 执行后钩子
     */
    protected void afterExecute() {
    }

    /**
     * 加载数据生命周期
     */
    protected void loadDataWrapper() {
        beforeLoadData();
        loadData();
        afterLoadData();
    }

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
     * 评测生命周期
     */
    protected void evalWrapper() {
        beforeEval();
        eval();
        afterEval();
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
     * 获取待处理数据量
     */
    public abstract long getRemainDataCount();

    /**
     * 获取已处理数据量
     */
    public abstract long getProcessedDataCount();
}
