package com.evalkit.framework.common.utils.time;

import org.apache.commons.lang3.time.DateFormatUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 日期处理工具类
 */
public class DateUtils {
    // 默认时间格式
    private final static String DEFAULT_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    // 默认地区
    private final static Locale DEFAULT_LOCALE = Locale.CHINA;


    private DateUtils() {
    }

    /**
     * 当前时间
     */
    public static Date now() {
        return new Date();
    }

    /**
     * 当前时间转字符串
     */
    public static String nowToString() {
        return DateFormatUtils.format(now(), DEFAULT_DATE_FORMAT, DEFAULT_LOCALE);
    }

    public static String nowToString(String format) {
        return DateFormatUtils.format(now(), format, DEFAULT_LOCALE);
    }

    /**
     * 时间转字符串
     */
    public static String dateToString(Date date) {
        return DateFormatUtils.format(date, DEFAULT_DATE_FORMAT, DEFAULT_LOCALE);
    }

    public static String dateToString(Date date, String format) {
        return DateFormatUtils.format(date, format, DEFAULT_LOCALE);
    }

    /**
     * 字符串转时间
     */
    public static Date parse(String dateStr) {
        return parse(dateStr, DEFAULT_DATE_FORMAT);
    }

    public static Date parse(String dateStr, String format) {
        try {
            return org.apache.commons.lang3.time.DateUtils.parseDate(dateStr, DEFAULT_LOCALE, format);
        } catch (ParseException e) {
            throw new RuntimeException("Parse date string error: " + e.getMessage(), e);
        }
    }

    /**
     * 时间转13位时间戳
     */
    public static long timestamp(Date date) {
        return date.getTime();
    }

    /**
     * 当前时间转13位时间戳
     */
    public static long nowTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 字符串转13位时间戳
     */
    public static long dateStringToTimestamp(String dateStr) {
        Date parse = parse(dateStr, DEFAULT_DATE_FORMAT);
        return parse.getTime();
    }

    public static long dateStringToTimestamp(String dateStr, String format) {
        Date parse = parse(dateStr, format);
        return parse.getTime();
    }

    /**
     * 计算耗时
     */
    public static long timeCost(Date start, Date end) {
        return end.getTime() - start.getTime();
    }

    public static long timeCost(String start, String end) {
        return timeCost(parse(start, DEFAULT_DATE_FORMAT), parse(end, DEFAULT_DATE_FORMAT));
    }

    public static long timeCost(String start, String end, String format) {
        return timeCost(parse(start, format), parse(end, format));
    }

    /**
     * 判断日期的pattern是否合法
     *
     * @param pattern 日期格式
     * @return true/false
     */
    public static boolean isValidPattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return false;
        }
        try {
            new SimpleDateFormat(pattern);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 给定日期增加天数
     *
     * @param date 给定日期
     * @param days 增加的天数
     * @return 增加天数后的日期
     */
    public static Date addDays(Date date, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DATE, days);
        return calendar.getTime();
    }
}
