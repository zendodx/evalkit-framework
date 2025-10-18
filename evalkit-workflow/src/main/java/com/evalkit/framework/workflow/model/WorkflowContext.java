package com.evalkit.framework.workflow.model;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流上下文
 */
public class WorkflowContext implements Cloneable {
    private Map<String, Object> store;

    public WorkflowContext() {
        this.store = new ConcurrentHashMap<>();
    }

    public void put(String key, Object value) {
        if (store == null) store = new ConcurrentHashMap<>();
        store.put(key, value);
    }

    public <T> T get(String key, Class<T> clazz) {
        if (store == null) return null;
        Object obj = store.getOrDefault(key, null);
        if (obj == null) return null;
        return clazz.cast(obj);
    }

    public List<String> keys() {
        return new ArrayList<>(store.keySet());
    }

    public boolean contains(String key) {
        return store.containsKey(key);
    }

    @Override
    public String toString() {
        return JsonUtils.toJson(store);
    }

    @Override
    public WorkflowContext clone() {
        try {
            WorkflowContext clone = (WorkflowContext) super.clone();
            String storeJson = JsonUtils.toJson(this.store);
            clone.store = new ConcurrentHashMap<>(JsonUtils.fromJson(storeJson, new TypeReference<Map<String, Object>>() {
            }));
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
