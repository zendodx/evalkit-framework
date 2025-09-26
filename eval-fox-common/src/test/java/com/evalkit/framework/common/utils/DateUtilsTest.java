package com.evalkit.framework.common.utils;

import com.evalkit.framework.common.utils.time.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
class DateUtilsTest {

    @Test
    void testNow() {
        Date now = DateUtils.now();
        assertNotNull(now);
        log.info("当前时间: {}", now);
    }

    @Test
    void testNowToString() {
        String nowString = DateUtils.nowToString();
        assertNotNull(nowString);
        log.info("当前时间字符串: {}", nowString);
    }

    @Test
    void testParse() {
        Date parse = DateUtils.parse("2025-09-25T12:12:12");
        assertNotNull(parse);
        log.info("时间: {}", parse);
    }

    @Test
    void testTimestamp() {
        long timestamp = DateUtils.timestamp(new Date());
        log.info("timestamp: {}", timestamp);
    }

    @Test
    void testNowTimestamp() {
        long timestamp = DateUtils.nowTimestamp();
        log.info("timestamp: {}", timestamp);
    }

    @Test
    void testDateStringToTimestamp() {
        long timestamp = DateUtils.dateStringToTimestamp("2025-09-25T12:12:12");
        log.info("dateStringToTimestamp: {}", timestamp);
    }

    @Test
    void testTimeCost() {
        long timeCost = DateUtils.timeCost("2025-09-25T12:12:12", "2025-09-26T12:12:12");
        log.info("timeCost: {}", timeCost);
    }
}