package com.evalkit.framework.eval.model.attribute.v2;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 异常分类
 */
@Data
public class Category {
    /* 分类名称 */
    private String categoryName;
    /* 分类编码 */
    private String categoryCode;
    /* 问题列表 */
    private List<Issue> issues = new ArrayList<>();
}
