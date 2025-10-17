package com.evalkit.framework.eval.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * api调用器结果
 */
@Data
@Builder
@AllArgsConstructor
public class ApiCompletionResult {
    /* 数据索引 */
    private Long dataIndex;
    /* 接口调用结果 */
    private Map<String, Object> resultItem;
    /* 调用开始时间 */
    private long startTime;
    /* 调用结束时间 */
    private long endTime;
    /* 调用耗时 */
    private long timeCost;
    /* 调用是否成功 */
    private boolean success;

    public ApiCompletionResult() {
    }

    public ApiCompletionResult(Map<String, Object> resultItem) {
        this.resultItem = resultItem;
    }

    public <T> T get(String key) {
        return get(key, null);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        if (resultItem == null) {
            return defaultValue;
        }
        return (T) resultItem.getOrDefault(key, defaultValue);
    }

    public void set(String key, Object value) {
        synchronized (this) {
            if (resultItem == null) {
                resultItem = new ConcurrentHashMap<>();
            }
            resultItem.put(key, value);
        }
    }
}
