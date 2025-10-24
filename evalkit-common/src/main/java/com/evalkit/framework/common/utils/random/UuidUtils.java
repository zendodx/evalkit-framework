package com.evalkit.framework.common.utils.random;

import java.util.UUID;

/**
 * UUID工具类
 */
public class UuidUtils {

    private UuidUtils() {
    }

    /**
     * 随机生成uuid
     *
     * @return uuid
     */
    public static String generateUuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * 根据key生成uuid,相同的key会产生相同的uuid
     *
     * @param key 给定的key
     * @return uuid
     */
    public static String generateUuidByKey(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes()).toString();
    }
}
