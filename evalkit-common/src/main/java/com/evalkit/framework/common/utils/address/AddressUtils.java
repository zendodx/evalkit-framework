package com.evalkit.framework.common.utils.address;

import com.evalkit.framework.common.utils.file.FileUtils;
import com.evalkit.framework.common.utils.json.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Address工具类
 */
public class AddressUtils {
    private AddressUtils() {
    }

    /**
     * 省市联动表
     */
    public static Map<String, List<String>> pc() {
        return JsonUtils.readJsonStream(FileUtils.openClasspath("classpath:address/pc.json"),
                new TypeReference<Map<String, List<String>>>() {
                });
    }

    /**
     * 省市区联动表
     */
    public static Map<String, Map<String, List<String>>> pca() {
        return JsonUtils.readJsonStream(FileUtils.openClasspath("classpath:address/pca.json"),
                new TypeReference<Map<String, Map<String, List<String>>>>() {
                });
    }

    /**
     * 省市区街道联动表
     */
    public static Map<String, Map<String, Map<String, List<String>>>> pcas() {
        return JsonUtils.readJsonStream(FileUtils.openClasspath("classpath:address/pcas.json"),
                new TypeReference<Map<String, Map<String, Map<String, List<String>>>>>() {
                });
    }

    /**
     * 获取某省的市联动表
     */
    public static Map<String, List<String>> ca(String province) {
        Map<String, Map<String, List<String>>> pca = pca();
        if (pca == null) return null;
        return pca.getOrDefault(province, null);
    }

    /**
     * 获取某省的市,区,街道联动表
     */
    public static Map<String, Map<String, List<String>>> cas(String province) {
        Map<String, Map<String, Map<String, List<String>>>> pcas = pcas();
        if (pcas == null) return null;
        return pcas.getOrDefault(province, null);
    }

    /**
     * 获取某省某市的区,街道联动表
     */
    public static Map<String, List<String>> as(String province, String city) {
        Map<String, Map<String, Map<String, List<String>>>> pcas = pcas();
        if (pcas == null) return null;
        Map<String, Map<String, List<String>>> cas = pcas.getOrDefault(province, null);
        if (cas == null) return null;
        return cas.getOrDefault(city, null);
    }

    /**
     * 获取省列表
     */
    public static List<String> getProvinceNames() {
        Map<String, List<String>> pc = pc();
        return new ArrayList<>(pc.keySet());
    }

    /**
     * 获取市列表
     */
    public static List<String> getCityNames() {
        Map<String, List<String>> pc = pc();
        List<String> cityNames = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : pc.entrySet()) {
            cityNames.addAll(entry.getValue());
        }
        return cityNames;
    }

    /**
     * 获取某省的市列表
     */
    public static List<String> getCityNamesByProvince(String province) {
        if (StringUtils.isEmpty(province)) return null;
        Map<String, List<String>> pc = pc();
        return pc.getOrDefault(province, null);
    }

    /**
     * 获取区县列表
     */
    public static List<String> getAreaNames() {
        Map<String, Map<String, List<String>>> pca = pca();
        List<String> areaNames = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<String>>> provinceEntry : pca.entrySet()) {
            Map<String, List<String>> provinceValue = provinceEntry.getValue();
            for (Map.Entry<String, List<String>> cityEntry : provinceValue.entrySet()) {
                areaNames.addAll(cityEntry.getValue());
            }
        }
        return areaNames;
    }

    /**
     * 获取某省的区县列表
     */
    public static List<String> getAreaNamesByProvince(String province) {
        if (StringUtils.isEmpty(province)) return null;
        Map<String, List<String>> cityMap = ca(province);
        if (cityMap == null) return null;
        List<String> areaNames = new ArrayList<>();
        for (Map.Entry<String, List<String>> cityEntry : cityMap.entrySet()) {
            areaNames.addAll(cityEntry.getValue());
        }
        return areaNames;
    }

    /**
     * 获取某省,某市的区县列表
     */
    public static List<String> getAreaNamesByProvinceAndCity(String province, String city) {
        if (StringUtils.isEmpty(city)) return null;
        Map<String, Map<String, List<String>>> pca = pca();
        Map<String, List<String>> cityMap = pca.getOrDefault(province, null);
        if (cityMap == null) return null;
        return cityMap.getOrDefault(city, null);
    }

    /**
     * 获取街道列表
     */
    public static List<String> getStreetNames() {
        Map<String, Map<String, Map<String, List<String>>>> pcas = pcas();
        List<String> streetNames = new ArrayList<>();
        for (Map.Entry<String, Map<String, Map<String, List<String>>>> streetEntry : pcas.entrySet()) {
            Map<String, Map<String, List<String>>> provinceValue = streetEntry.getValue();
            for (Map.Entry<String, Map<String, List<String>>> provinceEntry : provinceValue.entrySet()) {
                Map<String, List<String>> cityValue = provinceEntry.getValue();
                for (Map.Entry<String, List<String>> cityEntry : cityValue.entrySet()) {
                    streetNames.addAll(cityEntry.getValue());
                }
            }
        }
        return streetNames;
    }

    /**
     * 获取某省的街道列表
     */
    public static List<String> getStreetNamesByProvince(String province) {
        if (StringUtils.isEmpty(province)) return null;
        Map<String, Map<String, Map<String, List<String>>>> pcas = pcas();
        List<String> streetNames = new ArrayList<>();
        Map<String, Map<String, List<String>>> cityMap = pcas.getOrDefault(province, null);
        if (cityMap == null) return null;
        for (Map.Entry<String, Map<String, List<String>>> provinceEntry : cityMap.entrySet()) {
            Map<String, List<String>> cityValue = provinceEntry.getValue();
            for (Map.Entry<String, List<String>> cityEntry : cityValue.entrySet()) {
                streetNames.addAll(cityEntry.getValue());
            }
        }
        return streetNames;
    }

    /**
     * 获取某省,某市的街道列表
     */
    public static List<String> getStreetNamesByProvinceAndCity(String province, String city) {
        if (StringUtils.isEmpty(province)) return null;
        Map<String, Map<String, Map<String, List<String>>>> pcas = pcas();
        Map<String, Map<String, List<String>>> cityMap = pcas.getOrDefault(province, null);
        if (cityMap == null) return null;
        Map<String, List<String>> areaMap = cityMap.getOrDefault(city, null);
        if (areaMap == null) return null;
        List<String> streetNames = new ArrayList<>();
        for (Map.Entry<String, List<String>> cityEntry : areaMap.entrySet()) {
            streetNames.addAll(cityEntry.getValue());
        }
        return streetNames;
    }

    /**
     * 获取某省,某市,某区县的街道列表
     */
    public static List<String> getStreetNamesByProvinceAndCityAndArea(String province, String city, String area) {
        if (StringUtils.isEmpty(province)) return null;
        Map<String, Map<String, Map<String, List<String>>>> pcas = pcas();
        Map<String, Map<String, List<String>>> cityMap = pcas.getOrDefault(province, null);
        if (cityMap == null) return null;
        Map<String, List<String>> areaMap = cityMap.getOrDefault(city, null);
        if (areaMap == null) return null;
        return areaMap.getOrDefault(area, null);
    }

}
