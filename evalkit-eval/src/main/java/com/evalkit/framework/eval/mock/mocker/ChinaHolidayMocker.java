package com.evalkit.framework.eval.mock.mocker;

import com.evalkit.framework.common.utils.time.DateUtils;
import com.evalkit.framework.common.utils.time.HolidayUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * 节假日Mocker
 * <p>
 * {{holiday}}  随机返回一个公历或农历节假日
 * {{local_holiday}}  随机返回一个公历节假日
 * {{chinese_holiday}}  随机返回一个农历节假日
 * <p>
 * {{solr_term_holiday}}  随机返回一个节气
 * <p>
 * {{future_holiday}}   随机返回一个将来的公历或农历节假日
 * {{future_local_holiday}}  随机返回一个指定日期之后的公历节假日
 * {{future_chinese_holiday}}   随机返回一个指定日期之后的农历节假日
 * <p>
 * {{future_holiday 20250815}}  随机返回一个指定日期之后的公历或农历节假日
 * {{future_local_holiday 20250815}}  随机返回一个指定日期之后的公历节假日
 * {{future_chinese_holiday 20250815}}  随机返回一个指定日期之后的农历节假日
 * <p>
 * {{past_holiday}}  随机返回一个过去时间的公历或农历节假日
 * {{past_local_holiday}}   随机返回一个过去时间的公历节假日
 * {{past_chinese_holiday}}   随机返回一个过去时间的农历节假日
 * <p>
 * {{past_holiday 20250815}}  随机返回一个指定日期之前的公历或农历节假日
 * {{past_holiday 20250815}}  随机返回一个指定日期之前的公历节假日
 * {{past_chinese_holiday 20250815}}  随机返回一个指定日期之前的农历节假日
 * <p>
 * {{between_holiday 20250815 20250816}} 随机返回一个指定日期之间的公历或农历节假日
 * {{between_local_holiday 20250815 20250816}} 随机返回一个指定日期之间的公历节假日
 * {{between_chinese_holiday 20250815 20250816}} 随机返回一个指定日期之间的农历节假日
 */
@Slf4j
public class ChinaHolidayMocker implements Mocker {

    @Override
    public boolean support(String rule, List<String> args) {
        return StringUtils.contains(rule, "holiday");
    }

    @Override
    public String mock(String rule, List<String> args) {
        switch (rule) {
            case "holiday":
                return getHoliday("all");
            case "local_holiday":
                return getHoliday("local");
            case "chinese_holiday":
                return getHoliday("chinese");
            case "solr_term_holiday":
                return getSolarTermHoliday();
            case "future_holiday":
                return getFutureHoliday("all", args);
            case "future_local_holiday":
                return getFutureHoliday("local", args);
            case "future_chinese_holiday":
                return getFutureHoliday("chinese", args);
            case "past_holiday":
                return getPastHoliday("all", args);
            case "past_local_holiday":
                return getPastHoliday("local", args);
            case "past_chinese_holiday":
                return getPastHoliday("chinese", args);
            case "between_holiday":
                return getBetweenHoliday("all", args);
            case "between_local_holiday":
                return getBetweenHoliday("local", args);
            case "between_chinese_holiday":
                return getBetweenHoliday("chinese", args);
            default:
                return null;
        }
    }

    private String getHoliday(String type) {
        List<String> holidays = getHolidayList(type);
        return Mocker.randomChoose(holidays);
    }

    private String getSolarTermHoliday() {
        return Mocker.randomChoose(HolidayUtils.getSolrTerms());
    }

    private String getFutureHoliday(String type, List<String> args) {
        Date date = parseDateArg(args, 0, new Date());
        List<String> holidays = getHolidayListByDate(type, "future", date, null);
        return Mocker.randomChoose(holidays);
    }

    private String getPastHoliday(String type, List<String> args) {
        Date date = parseDateArg(args, 0, new Date());
        List<String> holidays = getHolidayListByDate(type, "past", date, null);
        return Mocker.randomChoose(holidays);
    }

    private String getBetweenHoliday(String type, List<String> args) {
        Date start = parseDateArg(args, 0, new Date());
        Date end = parseDateArg(args, 1, new Date());
        List<String> holidays = getHolidayListByDate(type, "between", start, end);
        return Mocker.randomChoose(holidays);
    }

    /**
     * 公共方法：根据类型获取节日列表
     */
    private List<String> getHolidayList(String type) {
        if ("all".equals(type)) {
            return HolidayUtils.getHolidays();
        } else if ("local".equals(type)) {
            return HolidayUtils.getLocalHolidays();
        } else if ("chinese".equals(type)) {
            return HolidayUtils.getChineseHolidays();
        }
        return null;
    }

    /**
     * 公共方法：根据类型和日期获取节日列表
     */
    private List<String> getHolidayListByDate(String type, String mode, Date date1, Date date2) {
        try {
            if ("future".equals(mode)) {
                if ("all".equals(type)) {
                    return HolidayUtils.getFutureHolidays(date1);
                } else if ("local".equals(type)) {
                    return HolidayUtils.getFutureLocalHolidays(date1);
                } else if ("chinese".equals(type)) {
                    return HolidayUtils.getFutureChineseHolidays(date1);
                }
            } else if ("past".equals(mode)) {
                if ("all".equals(type)) {
                    return HolidayUtils.getPastHolidays(date1);
                } else if ("local".equals(type)) {
                    return HolidayUtils.getPastLocalHolidays(date1);
                } else if ("chinese".equals(type)) {
                    return HolidayUtils.getPastChineseHolidays(date1);
                }
            } else if ("between".equals(mode)) {
                if ("all".equals(type)) {
                    return HolidayUtils.getBetweenHolidays(date1, date2);
                } else if ("local".equals(type)) {
                    return HolidayUtils.getBetweenLocalHolidays(date1, date2);
                } else if ("chinese".equals(type)) {
                    return HolidayUtils.getBetweenChineseHolidays(date1, date2);
                }
            }
        } catch (Exception e) {
            log.error("Get holiday list failed: type={}, mode={}, date1={}, date2={}", type, mode, date1, date2, e);
        }
        return null;
    }

    /**
     * 公共方法：解析日期参数
     */
    private Date parseDateArg(List<String> args, int index, Date defaultDate) {
        if (CollectionUtils.isNotEmpty(args) && args.size() > index) {
            try {
                return DateUtils.parse(args.get(index), "yyyyMMdd");
            } catch (Exception e) {
                log.error("Parse date argument failed: index={}, value={}", index, args.get(index), e);
            }
        }
        return defaultDate;
    }
}
