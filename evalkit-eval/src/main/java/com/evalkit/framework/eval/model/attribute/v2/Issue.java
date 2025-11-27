package com.evalkit.framework.eval.model.attribute.v2;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 问题
 */
@Data
public class Issue {
    /* 问题现象 */
    private String issueName;
    /* 问题编码 */
    private String issueCode;
    /* 问题置信度 */
    private double confidence;
    /* 问题情绪 */
    private Sentiment sentiment;
    /* 该问题包含的用例 */
    private List<Long> caseIds = new ArrayList<>();
    /* 50 字代表性描述 */
    private String representative;
}
