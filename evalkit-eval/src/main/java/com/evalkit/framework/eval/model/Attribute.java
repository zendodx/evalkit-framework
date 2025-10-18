package com.evalkit.framework.eval.model;

import lombok.Data;

import java.util.List;

/**
 * 归因口径模型
 */
@Data
public class Attribute {
    /* 问题名称 */
    private String issueName;
    /* 关联CaseId */
    private List<Long> caseIds;
}
