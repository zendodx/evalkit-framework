package com.evalkit.framework.workflow.utils;

import com.evalkit.framework.common.utils.random.NanoIdUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * 工作流工具类
 */
public class WorkflowUtils {
    private WorkflowUtils() {
    }

    /**
     * 生成节点id
     */
    public static String generateNodeId(String idPrefix) {
        if (StringUtils.isEmpty(idPrefix)) return NanoIdUtils.random();
        return idPrefix + NanoIdUtils.random(5);
    }
}
