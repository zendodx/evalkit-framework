package com.evalkit.framework.common.utils.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class NetworkUtilsTest {

    /**
     * 测试端口是否被占用
     */
    @Test
    public void testIsPortUsed() {
        boolean result = NetworkUtils.isPortUsed(61616);
        assertFalse(result);
    }
}