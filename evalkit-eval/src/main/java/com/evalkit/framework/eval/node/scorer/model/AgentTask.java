package com.evalkit.framework.eval.node.scorer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提交给外部/本地Agent执行的任务描述
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTask {
    /* 评测数据索引,便于日志追踪 */
    private Long dataIndex;
    /* 下发给Agent的任务指令/目标描述 */
    private String instruction;
    /* 任务附加参数,由具体Agent接入方自由扩展,如agentName、设备信息、上下文等 */
    private Map<String, Object> params;

    public <T> T get(String key) {
        return get(key, null);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        if (params == null) {
            return defaultValue;
        }
        return (T) params.getOrDefault(key, defaultValue);
    }

    public void set(String key, Object value) {
        synchronized (this) {
            if (params == null) {
                params = new ConcurrentHashMap<>();
            }
            params.put(key, value);
        }
    }
}

