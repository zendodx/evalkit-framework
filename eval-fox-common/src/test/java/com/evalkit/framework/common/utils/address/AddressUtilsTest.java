package com.evalkit.framework.common.utils.address;

import com.evalkit.framework.common.utils.address.AddressUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
class AddressUtilsTest {
    @Test
    public void testPc() {
        Map<String, List<String>> pc = AddressUtils.pc();
        log.info("pc:{}", pc);
        assertNotNull(pc);
    }

    @Test
    public void testPca() {
        Map<String, Map<String, List<String>>> pca = AddressUtils.pca();
        log.info("pca:{}", pca);
        assertNotNull(pca);
    }

    @Test
    public void testPcas() {
        Map<String, Map<String, Map<String, List<String>>>> pcas = AddressUtils.pcas();
        log.info("pcas:{}", pcas);
        assertNotNull(pcas);
    }

    @Test
    public void testCas() {
        String province = "河北省";
        Map<String, Map<String, List<String>>> cas = AddressUtils.cas(province);
        log.info("cas:{}", cas);
        assertNotNull(cas);
    }

    @Test
    public void testCa() {
        String province = "河北省";
        Map<String, List<String>> ca = AddressUtils.ca(province);
        log.info("ca:{}", ca);
        assertNotNull(ca);
    }

    @Test
    public void testAs() {
        String province = "河北省";
        String city = "石家庄市";
        Map<String, List<String>> as = AddressUtils.as(province, city);
        log.info("as:{}", as);
        assertNotNull(as);
    }

    @Test
    public void testGetProvinceNames() {
        List<String> provinceNames = AddressUtils.getProvinceNames();
        log.info("provinceNames:{}", provinceNames);
        assertNotNull(provinceNames);
    }

    @Test
    public void testGetCityNames() {
        List<String> cityNames = AddressUtils.getCityNames();
        log.info("cityNames:{}", cityNames);
        assertNotNull(cityNames);
    }

    @Test
    public void testGetCityNamesByProvince() {
        String province = "河北省";
        List<String> cityNames = AddressUtils.getCityNamesByProvince(province);
        log.info("province:{}, cityNames:{}", province, cityNames);
        assertNotNull(cityNames);
    }

    @Test
    public void testGetAreaNames() {
        List<String> areaNames = AddressUtils.getAreaNames();
        log.info("areaNames:{}", areaNames);
        assertNotNull(areaNames);
    }

    @Test
    public void testGetAreaNamesByProvince() {
        String province = "河北省";
        List<String> areaNames = AddressUtils.getAreaNamesByProvince(province);
        log.info("province:{}, areaNames:{}", province, areaNames);
        assertNotNull(areaNames);
    }

    @Test
    public void testGetAreaNamesByCity() {
        String province = "河北省";
        String city = "石家庄市";
        List<String> areaNames = AddressUtils.getAreaNamesByProvinceAndCity(province, city);
        log.info("province:{}, city:{}, areaNames:{}", province, city, areaNames);
        assertNotNull(areaNames);
    }

    @Test
    public void testGetStreetNames() {
        List<String> streetNames = AddressUtils.getStreetNames();
        log.info("streetNames:{}", streetNames);
        assertNotNull(streetNames);
    }

    @Test
    public void testGetStreetNamesByProvince() {
        String province = "河北省";
        List<String> streetNames = AddressUtils.getStreetNamesByProvince(province);
        log.info("streetNames:{}", streetNames);
        assertNotNull(streetNames);
    }


    @Test
    public void testGetStreetNamesByProvinceAndCity() {
        String province = "河北省";
        String city = "石家庄市";
        List<String> streetNames = AddressUtils.getStreetNamesByProvinceAndCity(province, city);
        log.info("streetNames:{}", streetNames);
        assertNotNull(streetNames);
    }

    @Test
    public void testGetStreetNamesByProvinceAndCityAndArea() {
        String province = "河北省";
        String city = "石家庄市";
        String area = "高邑县";
        List<String> streetNames = AddressUtils.getStreetNamesByProvinceAndCityAndArea(province, city, area);
        log.info("streetNames:{}", streetNames);
        assertNotNull(streetNames);
    }
}