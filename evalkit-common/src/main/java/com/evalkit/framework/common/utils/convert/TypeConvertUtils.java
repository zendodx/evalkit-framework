package com.evalkit.framework.common.utils.convert;

/**
 * 类型转换工具类
 */
public final class TypeConvertUtils {

    /**
     * 禁止实例化
     */
    private TypeConvertUtils() {
        throw new AssertionError("No TypeConvertUtils instances for you!");
    }

    /* ======================== 基本类型转换 ======================== */

    /**
     * 转换为 Integer
     *
     * @param obj 任意对象
     * @return Integer 或 null
     * @throws NumberFormatException 当字符串无法解析为整数时
     */
    public static Integer toInteger(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Integer) {
            return (Integer) obj;
        }
        // 去掉首尾空格，提升容错
        return Integer.parseInt(String.valueOf(obj).trim());
    }

    /**
     * 转换为 Long
     *
     * @param obj 任意对象
     * @return Long 或 null
     * @throws NumberFormatException 当字符串无法解析为长整数时
     */
    public static Long toLong(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Long) {
            return (Long) obj;
        }
        return Long.parseLong(String.valueOf(obj).trim());
    }

    /**
     * 转换为 Double
     *
     * @param obj 任意对象
     * @return Double 或 null
     * @throws NumberFormatException 当字符串无法解析为浮点数时
     */
    public static Double toDouble(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Double) {
            return (Double) obj;
        }
        return Double.parseDouble(String.valueOf(obj).trim());
    }

    /**
     * 转换为 String
     *
     * @param obj 任意对象
     * @return String 或 null（入参为 null 时）
     */
    public static String toString(Object obj) {
        // String.valueOf(null) 会返回 "null" 字符串，此处保持返回 null 的语义
        return obj == null ? null : String.valueOf(obj);
    }

    /**
     * 转换为 Boolean
     *
     * @param obj 任意对象
     * @return Boolean 或 null
     * 注：仅当字符串为 "true"（忽略大小写）时返回 true，其余均返回 false
     */
    public static Boolean toBoolean(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        return Boolean.parseBoolean(String.valueOf(obj).trim());
    }

    /* ======================== 泛型转换 ======================== */

    /**
     * 泛型强制类型转换
     *
     * @param obj   任意对象
     * @param clazz 目标类型 Class
     * @param <T>   目标类型
     * @return 目标类型实例 或 null
     * @throws ClassCastException 当对象无法转换为目标类型时
     */
    @SuppressWarnings("unchecked")
    public static <T> T toType(Object obj, Class<T> clazz) {
        if (obj == null || clazz == null) {
            return null;
        }
        // 如果类型已匹配，直接返回，避免多余的 cast
        if (clazz.isInstance(obj)) {
            return clazz.cast(obj);
        }
        throw new ClassCastException("Cannot convert " + obj.getClass() + " to " + clazz.getName());
    }
}