package com.evalkit.framework.common.client.sse;

/**
 * 判断流式接口调用完成的策略
 */
public interface FishedCheckStrategy {
    boolean isFinished(String chunk);
}
