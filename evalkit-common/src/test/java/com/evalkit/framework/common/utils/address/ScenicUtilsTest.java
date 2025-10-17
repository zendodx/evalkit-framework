package com.evalkit.framework.common.utils.address;

import com.evalkit.framework.common.utils.address.ScenicUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Slf4j
class ScenicUtilsTest {

    @Test
    public void testScenicP() {
        Map<String, List<String>> p = ScenicUtils.scenicP();
        log.info("p: {}", p);
        assertNotNull(p);
    }

    @Test
    public void testScenicPc() {
        Map<String, Map<String, List<String>>> pc = ScenicUtils.scenicPc();
        log.info("pc: {}", pc);
        assertNotNull(pc);
    }

    @Test
    public void testGetScenics() {
        List<String> scenics = ScenicUtils.getScenics();
        log.info("scenics: {}", scenics);
        assertNotNull(scenics);
    }

    @Test
    public void testGetScenariosByProvince() {
        List<String> scenics = ScenicUtils.getScenariosByProvince("河北省");
        log.info("scenics: {}", scenics);
        assertNotNull(scenics);

        scenics = ScenicUtils.getScenariosByProvince("xx省");
        log.info("scenics: {}", scenics);
        assertNull(scenics);
    }

    @Test
    public void testGetScenariosByProvinceAndCity() {
        List<String> scenics = ScenicUtils.getScenariosByProvinceAndCity("河北省", "秦皇岛市");
        log.info("scenics: {}", scenics);
        assertNotNull(scenics);

        scenics = ScenicUtils.getScenariosByProvinceAndCity("河北省", "xx市");
        log.info("scenics: {}", scenics);
        assertNull(scenics);
    }
}