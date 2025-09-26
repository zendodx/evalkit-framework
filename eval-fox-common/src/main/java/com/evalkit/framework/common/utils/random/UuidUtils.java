package com.evalkit.framework.common.utils.random;

import java.util.UUID;

/**
 * UUID工具类
 */
public class UuidUtils {

    private UuidUtils() {
    }

    /**
     * 生成uuid
     */
    public static String generateUuid() {
        return UUID.randomUUID().toString();
    }
}
