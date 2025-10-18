package com.evalkit.framework.common.utils.statics;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;

/**
 * 数学统计工具类
 */
public class StaticsUtils {

    private StaticsUtils() {
    }

    /**
     * 计算方差
     */
    public static double variance(List<Double> vals) {
        if (CollectionUtils.isEmpty(vals)) {
            throw new IllegalArgumentException("vals must not be empty");
        }
        double avg = vals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return vals.stream().mapToDouble(score -> Math.pow(score - avg, 2)).average().orElse(0.0);
    }

    /**
     * 计算标准差
     */
    public static double standardDeviation(List<Double> vals) {
        return Math.sqrt(variance(vals));
    }

    /**
     * 计算百分位数
     */
    public static <T extends Number & Comparable<T>> T tp(List<T> list, int TP) {
        if (CollectionUtils.isEmpty(list)) {
            throw new IllegalArgumentException("List must not be empty");
        }
        if (TP <= 0 || TP > 100) {
            throw new IllegalArgumentException("TP must be in (0,100]");
        }
        Collections.sort(list);
        int index = (int) Math.ceil(list.size() * TP / 100.0);
        return list.get(Math.max(index - 1, 0));
    }

    /**
     * 计算最小值
     */
    public static <T extends Number & Comparable<T>> T min(List<T> list) {
        return Collections.min(list);
    }

    /**
     * 计算最大值
     */
    public static <T extends Number & Comparable<T>> T max(List<T> list) {
        return Collections.max(list);
    }

    /**
     * 求和
     */
    public static <T extends Number> double sum(List<T> list) {
        return list.stream().mapToDouble(Number::doubleValue).sum();
    }

    /**
     * 平均值
     */
    public static <T extends Number> double avg(List<T> list) {
        if (CollectionUtils.isEmpty(list)) {
            return 0.0;
        }
        return sum(list) / list.size();
    }
}
