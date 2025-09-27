package com.evalkit.framework.common.utils.address;

import com.evalkit.framework.common.utils.file.FileUtils;
import com.evalkit.framework.common.utils.json.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 景区名称工具类
 */
public class ScenicUtils {
    private ScenicUtils() {
    }

    /**
     * 省内景区表
     */
    public static Map<String, List<String>> scenicP() {
        return JsonUtils.readJsonStream(FileUtils.openClasspath("classpath:poi/scenic_p.json"),
                new TypeReference<Map<String, List<String>>>() {
                });
    }

    /**
     * 省,市景区表
     */
    public static Map<String, Map<String, List<String>>> scenicPc() {
        return JsonUtils.readJsonStream(FileUtils.openClasspath("classpath:poi/scenic_pc.json"),
                new TypeReference<Map<String, Map<String, List<String>>>>() {
                });
    }

    /**
     * 获取国内景区列表
     */
    public static List<String> getScenics() {
        return scenicP().values().stream()
                .flatMap(List::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 获取某省景区列表
     */
    public static List<String> getScenariosByProvince(String province) {
        return scenicP().getOrDefault(province, null);
    }

    /**
     * 获取某省,某市景区列表
     */
    public static List<String> getScenariosByProvinceAndCity(String province, String city) {
        Map<String, Map<String, List<String>>> pc = scenicPc();
        Map<String, List<String>> c = pc.getOrDefault(province, null);
        if (c == null) return null;
        return c.getOrDefault(city, null);
    }
}
