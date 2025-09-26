package com.evalkit.framework.common.utils;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.common.utils.map.MapUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Map;

@Slf4j
class MapUtilsTest {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class User {
        private String name;
        private int age;
    }

    @Test
    void testBeanToMap() {
        User user = new User("admin", 20);
        Map<String, Object> map = MapUtils.beanToMap(user);
        log.info("对象转Map结果: {}", map);
    }

    @Test
    void testFlatten() {
        String json = "{\"alibaba\":{\"admin\":{\"age\":20},\t\"user\":{\"age\":18}},\"tencent\":{\"admin\":{\"age\":20},\t\"user\":{\"age\":18}}}";
        Map<String, Object> map = JsonUtils.fromJsonToMap(json, String.class, Object.class);
        Map<String, Object> flatten = MapUtils.flatten(map, "#");
        log.info("平铺结果: {}", flatten);
    }

    @Test
    void testUnFlatten() {
        String flattenJson = "{\"alibaba#admin#age\":20,\"alibaba#user#age\":18,\"tencent#admin#age\":20,\"tencent#user#age\":18}";
        Map<String, Object> unFlatten = MapUtils.unFlatten(JsonUtils.fromJsonToMap(flattenJson, String.class, Object.class), "#");
        log.info("反平铺结果: {}", unFlatten);
    }
}