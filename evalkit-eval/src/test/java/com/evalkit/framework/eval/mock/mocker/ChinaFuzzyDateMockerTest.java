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
    void testFuzzyDateWeek() {
        Set<String> fuzzyWeeks = new HashSet<>(Arrays.asList(
                "本周", "下周", "上周", "周末"
        ));
        String result = mocker.mock("fuzzy_date", Collections.singletonList("week"));
        log.info("result:{}", result);
        assertNotNull(result);
        assertTrue(fuzzyWeeks.contains(result), "Result should be one of fuzzy week expressions");
    }

    @Test
    void testFuzzyDateMonth() {
        Set<String> fuzzyMonths = new HashSet<>(Arrays.asList(
                "月初", "月中", "月末", "上旬", "中旬", "下旬", "月底"
        ));
        String result = mocker.mock("fuzzy_date", Collections.singletonList("month"));
        log.info("result:{}", result);
        assertNotNull(result);
        assertTrue(fuzzyMonths.contains(result), "Result should be one of fuzzy month expressions");
    }

    @Test
    void testFuzzyDateYear() {
        Set<String> fuzzyYears = new HashSet<>(Arrays.asList(
                "今年", "去年", "明年", "前年", "后年", "年初", "年中",
                "年底", "上半年", "下半年", "往年", "来年", "翌年", "经年", "历年"
        ));
        String result = mocker.mock("fuzzy_date", Collections.singletonList("year"));
        log.info("result:{}", result);
        assertNotNull(result);
        assertTrue(fuzzyYears.contains(result), "Result should be one of fuzzy year expressions");
    }

    @Test
    void testFuzzyDateSeason() {
        Set<String> fuzzySeasons = new HashSet<>(Arrays.asList(
                "一季度", "二季度", "三季度", "四季度",
                "春季", "夏季", "秋季", "冬季",
                "早春", "暮春", "初夏", "盛夏", "深秋", "初冬"
        ));
        String result = mocker.mock("fuzzy_date", Collections.singletonList("season"));
        log.info("result:{}", result);
        assertNotNull(result);
        assertTrue(fuzzySeasons.contains(result), "Result should be one of fuzzy season expressions");
    }

    @Test
    void testFuzzyDateHuman() {
        Set<String> fuzzyHumans = new HashSet<>(Arrays.asList(
                "过两天", "等会儿", "回头", "赶明儿"
        ));
        String result = mocker.mock("fuzzy_date", Collections.singletonList("human"));
        log.info("result:{}", result);
        assertNotNull(result);
        assertTrue(fuzzyHumans.contains(result), "Result should be one of fuzzy human expressions");
    }

    @RepeatedTest(50)
    void testFuzzyDateAllTypes() {
        String result = mocker.mock("fuzzy_date", Collections.emptyList());
        log.info("result:{}", result);
        assertNotNull(result);
        // 验证是模糊日期表达之一
        Set<String> allFuzzyDates = new HashSet<>(Arrays.asList(
                "近日", "近来", "最近", "日前", "不日", "即日", "当日",
                "改日", "他日", "来日", "昔日", "往日",
                "本周", "下周", "上周", "周末",
                "月初", "月中", "月末", "上旬", "中旬", "下旬", "月底",
                "今年", "去年", "明年", "前年", "后年", "年初", "年中",
                "年底", "上半年", "下半年", "往年", "来年", "翌年", "经年", "历年",
                "一季度", "二季度", "三季度", "四季度",
                "春季", "夏季", "秋季", "冬季",
                "早春", "暮春", "初夏", "盛夏", "深秋", "初冬",
                "过两天", "等会儿", "回头", "赶明儿"
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
                "本周", "下周", "上周", "周末",
                "月初", "月中", "月末", "上旬", "中旬", "下旬", "月底",
                "今年", "去年", "明年", "前年", "后年", "年初", "年中",
                "年底", "上半年", "下半年", "往年", "来年", "翌年", "经年", "历年",
                "一季度", "二季度", "三季度", "四季度",
                "春季", "夏季", "秋季", "冬季",
                "早春", "暮春", "初夏", "盛夏", "深秋", "初冬",
                "过两天", "等会儿", "回头", "赶明儿"
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
                "本周", "下周", "上周", "周末",
                "月初", "月中", "月末", "上旬", "中旬", "下旬", "月底",
                "今年", "去年", "明年", "前年", "后年", "年初", "年中",
                "年底", "上半年", "下半年", "往年", "来年", "翌年", "经年", "历年",
                "一季度", "二季度", "三季度", "四季度",
                "春季", "夏季", "秋季", "冬季",
                "早春", "暮春", "初夏", "盛夏", "深秋", "初冬",
                "过两天", "等会儿", "回头", "赶明儿"
        ));
        assertTrue(allFuzzyDates.contains(result));
    }
}