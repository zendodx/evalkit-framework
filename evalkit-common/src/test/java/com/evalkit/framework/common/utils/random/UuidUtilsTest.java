package com.evalkit.framework.common.utils.random;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
class UuidUtilsTest {

    @Test
    void testGenerateUuid() {
        String uuid = UuidUtils.generateUuid();
        log.info("uuid: {}", uuid);
        Assertions.assertNotNull(uuid);
    }

    @Test
    void testGenerateUuidByKey() {
        String key = "testKey";
        String uuid1 = UuidUtils.generateUuidByKey(key);
        String uuid2 = UuidUtils.generateUuidByKey(key);
        log.info("key: {}, u1:{}, u2:{}", key, uuid1, uuid2);
        Assertions.assertTrue(StringUtils.equals(uuid1, uuid2));
    }
}