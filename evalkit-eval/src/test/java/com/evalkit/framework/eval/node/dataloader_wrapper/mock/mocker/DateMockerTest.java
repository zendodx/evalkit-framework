package com.evalkit.framework.eval.node.dataloader_wrapper.mock.mocker;

import com.evalkit.framework.common.utils.time.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class DateMockerTest {
    private final DateMocker mocker = new DateMocker();

    @Test
    void testSupportRuleName() {
        assertTrue(mocker.support("date", null));
        assertTrue(mocker.support("future_date", null));
        assertTrue(mocker.support("past_date", null));
        assertFalse(mocker.support("random_string", null));
    }

    @Test
    void testNowStrategyDefaultPattern() throws ParseException {
        String result = mocker.mock("date", Collections.emptyList());
        assertNotNull(result);
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(result);
    }

    @Test
    void testNowStrategyCustomPattern() throws ParseException {
        String pattern = "yyyy/MM/dd";
        String result = mocker.mock("date", Collections.singletonList(pattern));
        assertNotNull(result);
        new SimpleDateFormat(pattern).parse(result);
    }

    @RepeatedTest(100)
    void testFutureDateWithinRange() throws ParseException {
        String result = mocker.mock("future_date", Arrays.asList("15", "365"));
        log.info("result:{}", result);
        assertNotNull(result);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        assertTrue(sdf.parse(result).after(DateUtils.addDays(new Date(), 14)));
        assertTrue(sdf.parse(result).before(DateUtils.addDays(new Date(), 366)));

    }

    @RepeatedTest(100)
    void testPastDateWithinRange() throws ParseException {
        String result = mocker.mock("past_date", Arrays.asList("15", "365"));
        log.info("result:{}", result);
        assertNotNull(result);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        assertTrue(sdf.parse(result).before(DateUtils.addDays(new Date(), -14)));
        assertTrue(sdf.parse(result).after(DateUtils.addDays(new Date(), -366)));
    }

    @RepeatedTest(100)
    void testFutureDateWithCustomPattern() throws ParseException {
        String result = mocker.mock("future_date", Arrays.asList("366", "yyyy/MM/dd"));
        log.info("result:{}", result);
        assertNotNull(result);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
        assertTrue(sdf.parse(result).before(DateUtils.addDays(new Date(), 366)));
    }

    @RepeatedTest(100)
    void testPastDateWithCustomPattern() throws ParseException {
        String result = mocker.mock("past_date", Arrays.asList("365", "yyyy/MM/dd"));
        log.info("result:{}", result);
        assertNotNull(result);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
        assertTrue(sdf.parse(result).after(DateUtils.addDays(new Date(), -366)));
    }

    @RepeatedTest(100)
    void testFutureDateWithinRangeWithCustomPattern() throws ParseException {
        String result = mocker.mock("future_date", Arrays.asList("15", "365", "yyyy/MM/dd"));
        log.info("result:{}", result);
        assertNotNull(result);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
        assertTrue(sdf.parse(result).after(DateUtils.addDays(new Date(), 14)));
        assertTrue(sdf.parse(result).before(DateUtils.addDays(new Date(), 366)));
    }

    @RepeatedTest(100)
    void testPastDateWithinRangeWithCustomPattern() throws ParseException {
        String result = mocker.mock("past_date", Arrays.asList("15", "365", "yyyy/MM/dd"));
        log.info("result:{}", result);
        assertNotNull(result);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
        assertTrue(sdf.parse(result).before(DateUtils.addDays(new Date(), -14)));
        assertTrue(sdf.parse(result).after(DateUtils.addDays(new Date(), -366)));
    }

    @Test
    void testInvalidArgsThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mocker.mock("future_date", Arrays.asList("abc", "xyz")));
        log.info(ex.getMessage());
        assertTrue(ex.getMessage().contains("Error parsing args"));
    }

    @Test
    void testUnsupportedRuleReturnsNull() {
        assertNull(mocker.mock("unknown_rule", Collections.emptyList()));
    }
}