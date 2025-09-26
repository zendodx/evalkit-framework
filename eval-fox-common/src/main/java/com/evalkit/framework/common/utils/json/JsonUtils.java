package com.evalkit.framework.common.utils.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
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
     * json转Map<K,V>
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
     * 用法：JacksonUtil.fromJson(json, new TypeReference<List<User>>() {})
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
            return fromJson(json, clazz);
        }
        Object document;
        try {
            document = Configuration.defaultConfiguration().jsonProvider().parse(json);
        } catch (Exception e) {
            throw new RuntimeException("Parse json error: " + e.getMessage(), e);
        }
        ReadContext ctx = JsonPath.parse(document);
        T obj;
        try {
            obj = ctx.read(jsonPath);
        } catch (Exception e) {
            throw new RuntimeException("Read json path " + jsonPath + " error:" + e.getMessage(), e);
        }
        return obj;
    }
}
