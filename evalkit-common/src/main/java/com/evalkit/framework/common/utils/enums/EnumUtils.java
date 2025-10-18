package com.evalkit.framework.common.utils.enums;


import com.evalkit.framework.common.constants.BaseEnum;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 枚举工具类
 */
public class EnumUtils {
    private static final Map<Class<?>, Map<Object, ? extends BaseEnum<?>>> CACHE = new ConcurrentHashMap<>();

    private EnumUtils() {
    }

    /**
     * 根据code获取枚举类型
     */
    @SuppressWarnings("unchecked")
    public static <E extends Enum<E> & BaseEnum<T>, T> E getByCode(Class<E> enumType, T code) {
        if (code == null) {
            return null;
        }
        // 每个枚举类只初始化一次
        Map<Object, ? extends BaseEnum<?>> codeMap = CACHE.computeIfAbsent(enumType,
                k -> buildCodeMap(enumType));
        return (E) codeMap.get(code);
    }

    private static <E extends Enum<E> & BaseEnum<?>> Map<Object, E> buildCodeMap(Class<E> enumType) {
        Map<Object, E> map = new ConcurrentHashMap<>();
        Arrays.stream(enumType.getEnumConstants()).forEach(e -> map.put(e.getCode(), e));
        return map;
    }
}
