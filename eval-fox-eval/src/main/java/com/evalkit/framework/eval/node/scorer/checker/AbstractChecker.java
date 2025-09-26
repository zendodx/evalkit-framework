package com.evalkit.framework.eval.node.scorer.checker;

import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.node.scorer.checker.config.CheckerConfig;
import com.evalkit.framework.eval.node.scorer.checker.model.CheckItem;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 检查器抽象类,封装了检查器的公共逻辑
 */
@Slf4j
@Data
public abstract class AbstractChecker implements Checker {
    protected CheckerConfig config;

    public AbstractChecker() {
        this.config = CheckerConfig.builder().build();
    }

    public AbstractChecker(CheckerConfig config) {
        this.config = config;
    }

    /**
     * 准备检查项
     */
    protected abstract List<CheckItem> prepareCheckItems(DataItem dataItem);

    /**
     * 执行检查,更新检查项结果
     */
    protected abstract void check(DataItem dataItem);

    /**
     * 检查前钩子
     */
    protected void beforeCheck(DataItem dataItem) {
    }

    /**
     * 检查后钩子
     */
    protected void afterCheck(DataItem dataItem) {
    }

    /**
     * 错误处理钩子
     */
    protected void onError(DataItem dataItem, Throwable e) {
    }

    /**
     * 包含钩子的检查
     */
    @Override
    public void checkWrapper(DataItem dataItem) {
        // 如果不支持检查则结束
        if (!support(dataItem)) return;
        try {
            // 获取当前检查器的检查项列表
            config.setCheckItems(prepareCheckItems(dataItem));
            beforeCheck(dataItem);
            // 执行检查获取检查项的结果
            check(dataItem);
            afterCheck(dataItem);
        } catch (Throwable e) {
            log.error("Checker [{}] error", getCheckName(), e);
            onError(dataItem, e);
            // 某个检查器出错时要抛出异常,标记评估器出错
            throw e;
        } finally {
            // 合并检查项结果
            mergeCheckItems(config.getCheckItems());
        }
    }

    /**
     * 合并检查项结果
     */
    public void mergeCheckItems(List<CheckItem> curCheckItems) {
        for (CheckItem curCheckItem : curCheckItems) {
            config.getCheckItems().stream()
                    .filter(checkItem -> checkItem.getName().equals(curCheckItem.getName()))
                    .findFirst().ifPresent(checkItem -> {
                        checkItem.setScore(curCheckItem.getScore());
                        checkItem.setReason(curCheckItem.getReason());
                    });
        }
    }

    /**
     * 整合各检查项分数,获取最终结果
     */
    @Override
    public double getScore() {
        return config.getStrategy().mergeScore(config.getCheckItems());
    }

    /**
     * 整合各不通过检查项理由,获取最终理由
     */
    @Override
    public String getReason() {
        String reason = "";
        if (config.getReason() == null) {
            reason = config.getCheckItems().stream()
                    .filter(item -> item.getScore() == 0.0)
                    .map(CheckItem::getReason)
                    .collect(Collectors.joining(" | "));
            config.setReason(reason);
        }
        return reason;
    }

    @Override
    public String getCheckName() {
        return config.getName();
    }

    @Override
    public List<CheckItem> getCheckItems() {
        return config.getCheckItems();
    }
}
