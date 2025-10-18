package com.evalkit.framework.eval.node.scorer.checker;


import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.node.scorer.checker.model.CheckItem;

import java.util.List;

/**
 * 检查器
 */
public interface Checker {
    /**
     * 是否运行该项检查
     */
    boolean support(DataItem dataItem);

    /**
     * 执行检查
     */
    void checkWrapper(DataItem dataItem);

    /**
     * 获取检查器分数
     */
    double getScore();

    /**
     * 获取检查器理由
     */
    String getReason();

    /**
     * 获取检查名称
     */
    String getCheckName();

    /**
     * 获取检查项结果
     */
    List<CheckItem> getCheckItems();
}
