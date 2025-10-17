package com.evalkit.framework.eval.model;

import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 输入数据
 */
@Data
public class InputData {
    private Long dataIndex;
    private Map<String, Object> inputItem;

    public InputData() {

    }

    public InputData(Map<String, Object> inputItem) {
        this(null, inputItem);
    }

    public InputData(Long dataIndex, Map<String, Object> inputItem) {
        this.dataIndex = dataIndex;
        if (inputItem == null) {
            this.inputItem = new ConcurrentHashMap<>();
        } else {
            this.inputItem = new ConcurrentHashMap<>(inputItem);
        }
    }

    public <T> T get(String key) {
        return get(key, null);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        if (inputItem == null) {
            return defaultValue;
        }
        return (T) inputItem.getOrDefault(key, defaultValue);
    }

    public void set(String key, Object value) {
        synchronized (this) {
            if (inputItem == null) {
                inputItem = new ConcurrentHashMap<>();
            }
            inputItem.put(key, value);
        }
    }
}
