package com.evalkit.framework.common.utils;

import com.evalkit.framework.common.utils.json.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

@Slf4j
class JsonUtilsTest {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class User {
        private String name;
        private int age;
    }

    @Test
    void testToJson() {
        User user = new User("admin", 20);
        log.info("序列化结果: {}", JsonUtils.toJson(user));
    }

    @Test
    void testFromJson() {
        User user = JsonUtils.fromJson("{\"name\":\"admin\",\"age\":20}", User.class);
        log.info("反序列化结果: {}", user);
    }

    @Test
    void testFromJsonToList() {
        List<User> users = JsonUtils.fromJsonToList("[{\"name\":\"admin\",\"age\":20},{\"name\":\"user\",\"age\":18}]", User.class);
        log.info("反序列化为List结果: {}", users);
    }

    @Test
    void testFromJsonToMap() {
        Map<String, Object> map = JsonUtils.fromJsonToMap("{\"name\":\"admin\",\"age\":20}", String.class, Object.class);
        log.info("反序列为Map结果: {}", map);
    }
}