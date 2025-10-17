package com.evalkit.framework.common.utils.time;

import com.xkzhangsan.time.LunarDate;
import com.xkzhangsan.time.holiday.ChineseHolidayEnum;
import com.xkzhangsan.time.holiday.HolidayUtil;
import com.xkzhangsan.time.holiday.LocalHolidayEnum;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 节假日工具类
 */
public class HolidayUtils {

    private HolidayUtils() {
    }

    /* 获取公历和农历节假日列表 */
    public static List<String> getHolidays() {
        List<String> holidays = new ArrayList<>();
        holidays.addAll(getLocalHolidays());
        holidays.addAll(getChineseHolidays());
        return holidays;
    }

    /* 获取公历节假日列表 */
    public static List<String> getLocalHolidays() {
        return Arrays.stream(LocalHolidayEnum.values()).map(LocalHolidayEnum::getName).collect(Collectors.toList());
    }

    /* 获取农历节假日列表 */
    public static List<String> getChineseHolidays() {
        return Arrays.stream(ChineseHolidayEnum.values()).map(ChineseHolidayEnum::getName).collect(Collectors.toList());
    }

    /* 获取节气列表 */
    public static List<String> getSolrTerms() {
        return Arrays.stream(LunarDate.solarTerms).collect(Collectors.toList());
    }

    /* 获取某个时间点的公历节假日列表 */
    public static List<String> getFutureLocalHolidays(Date date) {
        return getBetweenLocalHolidays(date, getLastDate());
    }

    public static List<String> getPastLocalHolidays(Date date) {
        return getBetweenLocalHolidays(getFirstDate(), date);
    }

    public static List<String> getBetweenLocalHolidays(Date start, Date end) {
        List<String> holidays = new ArrayList<>();
        Date cur = start;
        while (cur.before(end)) {
            String curHoliday = HolidayUtil.getLocalHoliday(cur);
            if (StringUtils.isNotEmpty(curHoliday)) {
                holidays.add(curHoliday);
            }
            cur = DateUtils.addDays(cur, 1);
        }
        return holidays;
    }

    /* 获取某个时间点的农历和公历节假日列表 */
    public static List<String> getFutureHolidays(Date date) {
        List<String> holidays = new ArrayList<>();
        holidays.addAll(getFutureLocalHolidays(date));
        holidays.addAll(getFutureChineseHolidays(date));
        return holidays;
    }

    public static List<String> getPastHolidays(Date date) {
        List<String> holidays = new ArrayList<>();
        holidays.addAll(getPastLocalHolidays(date));
        holidays.addAll(getPastChineseHolidays(date));
        return holidays;
    }

    public static List<String> getBetweenHolidays(Date start, Date end) {
        List<String> holidays = new ArrayList<>();
        holidays.addAll(getBetweenLocalHolidays(start, end));
        holidays.addAll(getBetweenChineseHolidays(start, end));
        return holidays;
    }

    /* 获取某个时间点的农历节假日列表 */
    public static List<String> getFutureChineseHolidays(Date date) {
        return getBetweenChineseHolidays(date, getLastDate());
    }

    public static List<String> getPastChineseHolidays(Date date) {
        return getBetweenChineseHolidays(getFirstDate(), date);
    }

    public static List<String> getBetweenChineseHolidays(Date start, Date end) {
        List<String> holidays = new ArrayList<>();
        Date cur = start;
        while (cur.before(end)) {
            String curHoliday = HolidayUtil.getChineseHoliday(cur);
            if (StringUtils.isNotEmpty(curHoliday)) {
                holidays.add(curHoliday);
            }
            cur = DateUtils.addDays(cur, 1);
        }
        return holidays;
    }

    /* 获取本年最后一天的日期 */
    public static Date getLastDate() {
        int currentYear = LocalDate.now().getYear();
        LocalDate lastDay = LocalDate.of(currentYear, 12, 31);
        return Date.from(lastDay.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /* 获取本年第一天的日期 */
    public static Date getFirstDate() {
        int currentYear = LocalDate.now().getYear();
        LocalDate firstDay = LocalDate.of(currentYear, 1, 1);
        return Date.from(firstDay.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
