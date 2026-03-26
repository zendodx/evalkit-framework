package com.evalkit.framework.eval.mock.mocker;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class ChinaFuzzyDateMockerTest {
    private final ChinaFuzzyDateMocker mocker = new ChinaFuzzyDateMocker();

    @Test
    void testSupportRuleName() {
        assertTrue(mocker.support("fuzzy_date", null));
        assertTrue(mocker.support("FUZZY_DATE", null));
        assertTrue(mocker.support("Fuzzy_Date", null));
        assertFalse(mocker.support("date", null));
        assertFalse(mocker.support("random_string", null));
    }

    @Test
    void testFuzzyDateDefault() {
        String result = mocker.mock("fuzzy_date", Collections.emptyList());
        log.info("result:{}", result);
        assertNotNull(result);
        // 应该返回模糊日期表达
        assertTrue(result.length() > 0);
    }

    @Test
    void testFuzzyDateDay() {
        Set<String> fuzzyDays = new HashSet<>(Arrays.asList(
                "近日", "近来", "最近", "日前", "不日", "即日", "当日",
                "改日", "他日", "来日", "昔日", "往日"
        ));
        String result = mocker.mock("fuzzy_date", Collections.singletonList("day"));
        log.info("result:{}", result);
        assertNotNull(result);
        assertTrue(fuzzyDays.contains(result), "Result should be one of fuzzy day expressions");
    }

    @Test
    void testFuzzyDateDayFuture() {
        Set<String> futureDays = new HashSet<>(Arrays.asList(
                "不日", "即日", "改日", "他日", "来日", "当日"
        ));
        String result = mocker.mock("fuzzy_date", Arrays.asList("day", "future"));
        log.info("result:{}", result);
        assertNotNull(result);
        assertTrue(futureDays.contains(result), "Result should be one of future fuzzy day expressions");
    }

    @Test
    void testFuzzyDateDayPast() {
        Set<String> pastDays = new HashSet<>(Arrays.asList(
                "近日", "近来", "最近", "日前", "昔日", "往日"
        ));
        String result = mocker.mock("fuzzy_date", Arrays.asList("day", "past"));
        log.info("result:{}", result);
        assertNotNull(result);
        assertTrue(pastDays.contains(result), "Result should be one of past fuzzy day expressions");
    }

    @Test
    void testFuzzyDateWeek() {
        Set<String> fuzzyWeeks = new HashSet<>(Arrays.asList(
                "本周", "下周", "上周", "周末", "未来一周", "未来二周", "未来三周",
                "上上周", "大上周", "过去一周", "过去二周", "过去三周"
        ));
        String result = mocker.mock("fuzzy_date", Collections.singletonList("week"));
        log.info("result:{}", result);
        assertNotNull(result);
        assertTrue(fuzzyWeeks.contains(result), "Result should be one of fuzzy week expressions");
    }

    @Test
    void testFuzzyDateWeekFuture() {
        Set<String> futureWeeks = new HashSet<>(Arrays.asList(
                "本周", "下周", "周末", "未来一周", "未来二周", "未来三周"
        ));
        String result = mocker.mock("fuzzy_date", Arrays.asList("week", "future"));
        log.info("result:{}", result);
        assertNotNull(result);
        assertTrue(futureWeeks.contains(result), "Result should be one of future fuzzy week expressions");
    }

    @Test
    void testFuzzyDateWeekPast() {
        Set<String> pastWeeks = new HashSet<>(Arrays.asList(
                "上周", "上上周", "大上周", "过去一周", "过去二周", "过去三周"
        ));
        String result = mocker.mock("fuzzy_date", Arrays.asList("week", "past"));
        log.info("result:{}", result);
        assertNotNull(result);
        assertTrue(pastWeeks.contains(result), "Result should be one of past fuzzy week expressions");
    }

    @Test
    void testFuzzyDateMonth() {
        Set<String> fuzzyMonths = new HashSet<>(Arrays.asList(
                "月初", "月中", "月末", "上旬", "中旬", "下旬", "月底",
                "上月", "未来一月", "未来二月", "未来三月",
                "过去一月", "过去二月", "过去三月"
        ));
        String result = mocker.mock("fuzzy_date", Collections.singletonList("month"));
        log.info("result:{}", result);
        assertNotNull(result);
        assertTrue(fuzzyMonths.contains(result), "Result should be one of fuzzy month expressions");
    }

    @Test
    void testFuzzyDateMonthFuture() {
        Set<String> futureMonths = new HashSet<>(Arrays.asList(
                "月初", "月中", "月末", "上旬", "中旬", "下旬", "月底",
                "未来一月", "未来二月", "未来三月"
        ));
        String result = mocker.mock("fuzzy_date", Arrays.asList("month", "future"));
        log.info("result:{}", result);
        assertNotNull(result);
        assertTrue(futureMonths.contains(result), "Result should be one of future fuzzy month expressions");
    }

    @Test
    void testFuzzyDateMonthPast() {
        Set<String> pastMonths = new HashSet<>(Arrays.asList(
                "上月", "过去一月", "过去二月", "过去三月"
        ));
        String result = mocker.mock("fuzzy_date", Arrays.asList("month", "past"));
        log.info("result:{}", result);
        assertNotNull(result);
        assertTrue(pastMonths.contains(result), "Result should be one of past fuzzy month expressions");
    }

    @Test
    void testFuzzyDateYear() {
        Set<String> fuzzyYears = new HashSet<>(Arrays.asList(
                "今年", "去年", "明年", "前年", "后年", "年初", "年中",
                "年底", "上半年", "下半年", "往年", "来年", "翌年", "经年", "历年",
                "未来一年", "未来二年", "未来三年",
                "过去一年", "过去二年", "过去三年", "往年同期"
        ));
        String result = mocker.mock("fuzzy_date", Collections.singletonList("year"));
        log.info("result:{}", result);
        assertNotNull(result);
        assertTrue(fuzzyYears.contains(result), "Result should be one of fuzzy year expressions");
    }

    @Test
    void testFuzzyDateYearFuture() {
        Set<String> futureYears = new HashSet<>(Arrays.asList(
                "今年", "明年", "后年", "年初", "年中", "年底", "下半年",
                "来年", "翌年", "未来一年", "未来二年", "未来三年"
        ));
        String result = mocker.mock("fuzzy_date", Arrays.asList("year", "future"));
        log.info("result:{}", result);
        assertNotNull(result);
        assertTrue(futureYears.contains(result), "Result should be one of future fuzzy year expressions");
    }

    @Test
    void testFuzzyDateYearPast() {
        Set<String> pastYears = new HashSet<>(Arrays.asList(
                "去年", "前年", "往年", "往年同期", "历年", "经年", "上半年",
                "过去一年", "过去二年", "过去三年"
        ));
        String result = mocker.mock("fuzzy_date", Arrays.asList("year", "past"));
        log.info("result:{}", result);
        assertNotNull(result);
        assertTrue(pastYears.contains(result), "Result should be one of past fuzzy year expressions");
    }

    @Test
    void testFuzzyDateHuman() {
        Set<String> fuzzyHumans = new HashSet<>(Arrays.asList(
                "过两天", "等会儿", "回头", "赶明儿",
                "前两三天", "前几天"
        ));
        String result = mocker.mock("fuzzy_date", Collections.singletonList("human"));
        log.info("result:{}", result);
        assertNotNull(result);
        assertTrue(fuzzyHumans.contains(result), "Result should be one of fuzzy human expressions");
    }

    @Test
    void testFuzzyDateHumanFuture() {
        Set<String> futureHumans = new HashSet<>(Arrays.asList(
                "过两天", "等会儿", "回头", "赶明儿"
        ));
        String result = mocker.mock("fuzzy_date", Arrays.asList("human", "future"));
        log.info("result:{}", result);
        assertNotNull(result);
        assertTrue(futureHumans.contains(result), "Result should be one of future fuzzy human expressions");
    }

    @Test
    void testFuzzyDateHumanPast() {
        Set<String> pastHumans = new HashSet<>(Arrays.asList(
                "前两三天", "前几天"
        ));
        String result = mocker.mock("fuzzy_date", Arrays.asList("human", "past"));
        log.info("result:{}", result);
        assertNotNull(result);
        assertTrue(pastHumans.contains(result), "Result should be one of past fuzzy human expressions");
    }

    @Test
    void testFuzzyDateFutureOnly() {
        String result = mocker.mock("fuzzy_date", Collections.singletonList("future"));
        log.info("result:{}", result);
        assertNotNull(result);
        Set<String> allFutureDates = new HashSet<>(Arrays.asList(
                "不日", "即日", "改日", "他日", "来日", "当日",
                "本周", "下周", "周末", "未来一周", "未来二周", "未来三周",
                "月初", "月中", "月末", "上旬", "中旬", "下旬", "月底", "未来一月", "未来二月", "未来三月",
                "今年", "明年", "后年", "年初", "年中", "年底", "下半年", "来年", "翌年", "未来一年", "未来二年", "未来三年",
                "二季度", "三季度", "四季度", "夏季", "秋季", "冬季", "初夏", "盛夏", "深秋", "初冬",
                "过两天", "等会儿", "回头", "赶明儿"
        ));
        assertTrue(allFutureDates.contains(result), "Result should be one of future fuzzy date expressions");
    }

    @Test
    void testFuzzyDatePastOnly() {
        String result = mocker.mock("fuzzy_date", Collections.singletonList("past"));
        log.info("result:{}", result);
        assertNotNull(result);
        Set<String> allPastDates = new HashSet<>(Arrays.asList(
                "近日", "近来", "最近", "日前", "昔日", "往日",
                "上周", "上上周", "大上周", "过去一周", "过去二周", "过去三周",
                "上月", "过去一月", "过去二月", "过去三月",
                "去年", "前年", "往年", "往年同期", "历年", "经年", "上半年", "过去一年", "过去二年", "过去三年",
                "去年春季", "去年夏季", "去年秋季", "去年冬季", "前年春季", "前年夏季", "前年秋季", "前年冬季",
                "前两三天", "前几天"
        ));
        assertTrue(allPastDates.contains(result), "Result should be one of past fuzzy date expressions");
    }

    @RepeatedTest(50)
    void testFuzzyDateAllTypes() {
        String result = mocker.mock("fuzzy_date", Collections.emptyList());
        log.info("result:{}", result);
        assertNotNull(result);
        // 验证是模糊日期表达之一
        Set<String> allFuzzyDates = new HashSet<>(Arrays.asList(
                // 日期
                "近日", "近来", "最近", "日前", "不日", "即日", "当日",
                "改日", "他日", "来日", "昔日", "往日",
                // 周
                "本周", "下周", "上周", "周末", "未来一周", "未来二周", "未来三周",
                "上上周", "大上周", "过去一周", "过去二周", "过去三周",
                // 月
                "月初", "月中", "月末", "上旬", "中旬", "下旬", "月底",
                "上月", "未来一月", "未来二月", "未来三月", "过去一月", "过去二月", "过去三月",
                // 年
                "今年", "去年", "明年", "前年", "后年", "年初", "年中",
                "年底", "上半年", "下半年", "往年", "来年", "翌年", "经年", "历年",
                "未来一年", "未来二年", "未来三年", "过去一年", "过去二年", "过去三年", "往年同期",
                // 季节
                "二季度", "三季度", "四季度", "夏季", "秋季", "冬季",
                "初夏", "盛夏", "深秋", "初冬",
                "去年春季", "去年夏季", "去年秋季", "去年冬季",
                "前年春季", "前年夏季", "前年秋季", "前年冬季",
                // 口语
                "过两天", "等会儿", "回头", "赶明儿", "前两三天", "前几天"
        ));
        assertTrue(allFuzzyDates.contains(result), "Result should be one of all fuzzy date expressions");
    }

    @Test
    void testFuzzyDateCaseInsensitive() {
        String result = mocker.mock("fuzzy_date", Collections.singletonList("DAY"));
        log.info("result:{}", result);
        assertNotNull(result);
        Set<String> fuzzyDays = new HashSet<>(Arrays.asList(
                "近日", "近来", "最近", "日前", "不日", "即日", "当日",
                "改日", "他日", "来日", "昔日", "往日"
        ));
        assertTrue(fuzzyDays.contains(result), "Result should be one of fuzzy day expressions");
    }

    @Test
    void testFuzzyDateUnsupportedTypeUsesAll() {
        String result = mocker.mock("fuzzy_date", Collections.singletonList("invalid_type"));
        log.info("result:{}", result);
        assertNotNull(result);
        // 无效类型应该返回所有类型中的一个
        Set<String> allFuzzyDates = new HashSet<>(Arrays.asList(
                "近日", "近来", "最近", "日前", "不日", "即日", "当日",
                "改日", "他日", "来日", "昔日", "往日",
                "本周", "下周", "上周", "周末", "未来一周", "未来二周", "未来三周",
                "上上周", "大上周", "过去一周", "过去二周", "过去三周",
                "月初", "月中", "月末", "上旬", "中旬", "下旬", "月底",
                "上月", "未来一月", "未来二月", "未来三月", "过去一月", "过去二月", "过去三月",
                "今年", "去年", "明年", "前年", "后年", "年初", "年中",
                "年底", "上半年", "下半年", "往年", "来年", "翌年", "经年", "历年",
                "未来一年", "未来二年", "未来三年", "过去一年", "过去二年", "过去三年", "往年同期",
                "二季度", "三季度", "四季度", "夏季", "秋季", "冬季",
                "初夏", "盛夏", "深秋", "初冬",
                "去年春季", "去年夏季", "去年秋季", "去年冬季",
                "前年春季", "前年夏季", "前年秋季", "前年冬季",
                "过两天", "等会儿", "回头", "赶明儿", "前两三天", "前几天"
        ));
        assertTrue(allFuzzyDates.contains(result));
    }

    @Test
    void testUnsupportedRuleReturnsNull() {
        assertNull(mocker.mock("date", Collections.emptyList()));
        assertNull(mocker.mock("random_string", Collections.emptyList()));
    }

    @Test
    void testNullParamsDefaultsToAll() {
        String result = mocker.mock("fuzzy_date", null);
        assertNotNull(result);
        Set<String> allFuzzyDates = new HashSet<>(Arrays.asList(
                "近日", "近来", "最近", "日前", "不日", "即日", "当日",
                "改日", "他日", "来日", "昔日", "往日",
                "本周", "下周", "上周", "周末", "未来一周", "未来二周", "未来三周",
                "上上周", "大上周", "过去一周", "过去二周", "过去三周",
                "月初", "月中", "月末", "上旬", "中旬", "下旬", "月底",
                "上月", "未来一月", "未来二月", "未来三月", "过去一月", "过去二月", "过去三月",
                "今年", "去年", "明年", "前年", "后年", "年初", "年中",
                "年底", "上半年", "下半年", "往年", "来年", "翌年", "经年", "历年",
                "未来一年", "未来二年", "未来三年", "过去一年", "过去二年", "过去三年", "往年同期",
                "二季度", "三季度", "四季度", "夏季", "秋季", "冬季",
                "初夏", "盛夏", "深秋", "初冬",
                "去年春季", "去年夏季", "去年秋季", "去年冬季",
                "前年春季", "前年夏季", "前年秋季", "前年冬季",
                "过两天", "等会儿", "回头", "赶明儿", "前两三天", "前几天"
        ));
        assertTrue(allFuzzyDates.contains(result));
    }
}