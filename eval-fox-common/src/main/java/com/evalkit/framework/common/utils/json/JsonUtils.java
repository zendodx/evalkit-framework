package com.evalkit.framework.common.utils.json;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONPath;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 序列化工具类
 */
public class JsonUtils {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private JsonUtils() {
    }

    /**
     * 对象转json
     */
    public static String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Convert to json error:" + e.getMessage(), e);
        }
    }

    /**
     * json转对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("From json error:" + e.getMessage(), e);
        }
    }

    /**
     * json转对象列表
     */
    public static <T> List<T> fromJsonToList(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("From json to list error:" + e.getMessage(), e);
        }
    }

    /**
     * json转Map
     */
    public static <K, V> Map<K, V> fromJsonToMap(String json, Class<K> clazzK, Class<V> clazzV) {
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructMapType(Map.class, clazzK, clazzV));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("From json to map error:" + e.getMessage(), e);
        }
    }

    /**
     * json转复杂泛型对象
     */
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("From json to typeReference error:" + e.getMessage(), e);
        }
    }

    /**
     * 对象写json文件
     */
    public static void writeJsonFile(String filePath, Object obj) {
        try {
            objectMapper.writeValue(new File(filePath), obj);
        } catch (IOException e) {
            throw new RuntimeException("Write obj to json file error:" + e.getMessage(), e);
        }
    }

    /**
     * 读取json文件
     */
    public static <T> T readJsonFile(File filePath, Class<T> clazz) {
        try {
            return objectMapper.readValue(filePath, clazz);
        } catch (IOException e) {
            throw new RuntimeException("Read file to obj error:" + e.getMessage(), e);
        }
    }

    public static <T> T readJsonFile(File filePath, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(filePath, typeReference);
        } catch (IOException e) {
            throw new RuntimeException("Read file to obj error:" + e.getMessage(), e);
        }
    }

    /**
     * 输入流转对象
     */
    public static <T> T readJsonStream(InputStream inputStream, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(inputStream, typeReference);
        } catch (IOException e) {
            throw new RuntimeException("Read input stream to obj error:" + e.getMessage(), e);
        }
    }

    /**
     * json转对象,可按jsonpath提取部分字段
     */
    public static <T> T fromJson(String json, String jsonPath, Class<T> clazz) {
        if (StringUtils.isEmpty(jsonPath)) {
            // 全量反序列化
            return JSON.parseObject(json, clazz);
        }
        Object root;
        try {
            root = JSON.parse(json);
        } catch (Exception e) {
            throw new RuntimeException("Parse json error: " + e.getMessage(), e);
        }
        Object value;
        try {
            value = JSONPath.eval(root, jsonPath);
        } catch (Exception e) {
            throw new RuntimeException("Read json path " + jsonPath + " error: " + e.getMessage(), e);
        }
        // 类型转换
        if (clazz.isInstance(value)) {
            return clazz.cast(value);
        } else {
            // value 可能是 JSONObject/JSONArray 或基本类型
            // 先转为字符串再反序列化为目标类型
            try {
                String jsonStr = JSON.toJSONString(value);
                return JSON.parseObject(jsonStr, clazz);
            } catch (Exception e) {
                throw new RuntimeException("Convert value to class " + clazz.getName() + " error: " + e.getMessage(), e);
            }
        }
    }
}
