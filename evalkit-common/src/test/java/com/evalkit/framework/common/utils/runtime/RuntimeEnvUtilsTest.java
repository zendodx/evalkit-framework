package com.evalkit.framework.common.utils.runtime;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
class RuntimeEnvUtilsTest {

    @Test
    void getJVMPropertyBoolean() {
        boolean open = RuntimeEnvUtils.getJVMPropertyBoolean("open", false);
        log.info("open:{}", open);
        Assertions.assertFalse(open);

        RuntimeEnvUtils.setJVMProperty("open", "true");
        open = RuntimeEnvUtils.getJVMPropertyBoolean("open", false);
        log.info("open:{}", open);
        Assertions.assertTrue(open);
    }
}