package com.evalkit.framework.common.utils.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Map工具类
 */
public class MapUtils {
    private static final String DEFAULT_JOIN_STR = "_";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private MapUtils() {
    }

    /**
     * Map转对象
     */
    public static <T> T fromMap(Map<String, Object> map, Class<T> clazz) {
        try {
            return objectMapper.convertValue(map, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Convert bean to map error:" + e.getMessage(), e);
        }
    }

    /**
     * 对象转Map
     */
    public static <T> Map<String, Object> beanToMap(T bean) {
        try {
            return objectMapper.convertValue(bean, objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception e) {
            throw new RuntimeException("Convert bean to map error:" + e.getMessage(), e);
        }
    }

    /**
     * 多级Map平铺, joinStr表示连接符, 不支持数组形式的平铺
     */
    public static Map<String, Object> flatten(Map<String, Object> source, String joinStr) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (StringUtils.isBlank(joinStr)) {
            joinStr = DEFAULT_JOIN_STR;
        }
        flatten(null, source, result, joinStr);
        return result;
    }

    public static void flatten(String prefix, Object current, Map<String, Object> target, String joinStr) {
        if (current instanceof Map) {
            ((Map<?, ?>) current).forEach((k, v) -> {
                String key = prefix == null ? k.toString() : prefix + joinStr + k;
                flatten(key, v, target, joinStr);
            });
        } else if (current instanceof List) {
            List<?> list = (List<?>) current;
            for (int i = 0; i < list.size(); i++) {
                flatten(prefix + joinStr + i, list.get(i), target, joinStr);
            }
        } else {
            target.put(prefix, current);
        }
    }

    /**
     * 平铺Map还原
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> unFlatten(Map<String, Object> flat, String joinStr) {
        if (StringUtils.isEmpty(joinStr)) {
            joinStr = DEFAULT_JOIN_STR;
        }
        Map<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : flat.entrySet()) {
            String[] parts = e.getKey().split(joinStr);
            Map<String, Object> curr = root;
            for (int i = 0; i < parts.length - 1; i++) {
                curr = (Map<String, Object>) curr.computeIfAbsent(parts[i], k -> new LinkedHashMap<>());
            }
            curr.put(parts[parts.length - 1], e.getValue());
        }
        return root;
    }

    /**
     * 快速创建Map
     */
    public static <K, V> Map<K, V> of(K k1, V v1) {
        Map<K, V> map = new LinkedHashMap<>();
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2) {
        Map<K, V> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3) {
        Map<K, V> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        Map<K, V> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        Map<K, V> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) {
        Map<K, V> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        map.put(k6, v6);
        return map;
    }
}
