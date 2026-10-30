package com.evalkit.framework.eval.node.scorer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent任务的执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecutionResult {
    /* Agent任务ID/句柄 */
    private String taskId;
    /* 任务最终执行状态 */
    private AgentTaskStatus status;
    /* Agent执行过程/结果摘要,可用于生成打分理由 */
    private String summary;
    /* 失败时的错误信息 */
    private String errorMsg;
    /* 执行结果的扩展信息,如执行轨迹、截图URL列表、原始响应体等 */
    private Map<String, Object> extra;

    public <T> T get(String key) {
        return get(key, null);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        if (extra == null) {
            return defaultValue;
        }
        return (T) extra.getOrDefault(key, defaultValue);
    }

    public void set(String key, Object value) {
        synchronized (this) {
            if (extra == null) {
                extra = new ConcurrentHashMap<>();
            }
            extra.put(key, value);
        }
    }
}